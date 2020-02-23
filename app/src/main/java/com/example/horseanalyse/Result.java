package com.example.horseanalyse;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;

import com.example.horseanalyse.util.Document;
import com.example.horseanalyse.util.Md5;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Result extends AppCompatActivity {

    private RecyclerView recyclerView;
    private RecyclerView.Adapter mAdapter;
    private RecyclerView.LayoutManager layoutManager;
    private List<String> input;

    private String normalPath;
    private String frontPath;
    private String rearPath;

    private final String serverHost = "115.146.84.229";
    //private final String serverHost = "10.13.21.144";
    private final int serverPort = 8111;

    public final String TAG = "(horse-box)";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        Intent intent = getIntent();
        normalPath = intent.getStringExtra("NormalPath");
        frontPath = intent.getStringExtra("FrontPath");
        rearPath = intent.getStringExtra("RearPath");

        recyclerView = (RecyclerView) findViewById(R.id.result_list);
        recyclerView.setHasFixedSize(true);
        // use a linear layout manager
        layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        input = new ArrayList<>();
        //for (int i = 0; i < 10; i++) {
        input.add("Connecting with server");
        //}// define an adapter
        mAdapter = new MyAdapter(input);
        recyclerView.setAdapter(mAdapter);



        final File normalFile = new File(normalPath);
        final File frontFile = new File(frontPath);
        final File rearFile = new File(rearPath);

        Thread s = new Thread(new Runnable() {
            @Override
            public void run() {
                Result.this.client(normalFile, frontFile, rearFile);
            }
        });
        s.start();

    }

    private void client(File normalFile, File frontFile, File rearFile) {
        try {
            // Establish connection
            Socket socket = new Socket(serverHost, serverPort);
            BufferedReader bufferIn = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter bufferOut = new BufferedWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8));

            // input stream
            Document inputString;
            String command;

            // Add files into an array
            ArrayList<File> files = new ArrayList<>();
            files.add(normalFile);
            files.add(frontFile);
            files.add(rearFile);

            // let's do something mess up
            for (int i = 0 ; i < 3; i++){
                // get MD5 of file
                final String md5 = Md5.getFileMD5(files.get(i));

                // get lastModified attribute of file
                final long lastModDate = files.get(i).lastModified();

                // get file name of file
                final String fileName = files.get(i).getName();

                // get file size of file
                final long fileSize = files.get(i).length();

                // Send FILE_CREATE_REQUEST to server
                bufferOut.write(protocolFileSendRequest(md5, lastModDate, fileSize, fileName).toJson() + "\n");
                bufferOut.flush();

                // Get response from server
                inputString = inputRead(bufferIn);
                command = inputString.getString("command");

                if (command.equals("FILE_CREATE_RESPONSE")) {
                    addItem("Start sending " + files.get(i).getName());
                }

                inputString = inputRead(bufferIn);
                command = inputString.getString("command");

                addItem("Uploading " + files.get(i).getName() + "===0.00 %");

                while (command != null) {
                    long position = inputString.getLong("position");
                    long length = inputString.getLong("length");
                    System.out.println(inputString.toJson());
                    if (command.equals("FILE_BYTES_REQUEST")) {
                        updateItem(input.size()-1, "Uploading " +
                                files.get(i).getName() + "===" + String.format("%.2f",
                                ((double) (position + length) / (double) fileSize) * 100) +
                                " %");
                        ByteBuffer fileBytes = readFile(files.get(i).getAbsolutePath(), position, length);
                        String content = Base64.encodeToString(fileBytes.array(), Base64.NO_WRAP);
                        Document protocol = protocolBytesResponse(inputString, content, "successful read", true);
                        bufferOut.write(protocol.toJson() + "\n");
                        bufferOut.flush();

                    }

                    if (position + length == fileSize)
                        break;


                    inputString = inputRead(bufferIn);
                    command = inputString.getString("command");
                }
            }


            

            addItem("Processing videos....");

            //send heart bag
            Document protocol = protocolHeart();
            bufferOut.write(protocol.toJson() + "\n");
            bufferOut.flush();
            Log.i(TAG, "client: " + "Heart package sent");


            inputString = inputRead(bufferIn);
            command = inputString.getString("command");

            while (command != null) {
                if (command.equals("RESULT")) {
                    if (inputString.getBoolean("status")) {
                        String results = inputString.getString("message");
                        addItem(results);
                        break;
                    } else {
                        protocol = protocolHeart();
                        bufferOut.write(protocol.toJson() + "\n");
                        bufferOut.flush();
                        Log.i(TAG, "client: " + "Heart package sent");
                        Thread.sleep(5000);
                    }
                }

                inputString = inputRead(bufferIn);
                command = inputString.getString("command");

            }

            socket.close();

        } catch (Exception e) {
            addItem("Connection broken");
        }
    }

    private void addItem(String content){
        input.add(content);
        recyclerView.post(new Runnable()
        {
            @Override
            public void run() {
                mAdapter.notifyDataSetChanged();
            }
        });
        //mAdapter.notifyItemInserted(input.size()-1);
        recyclerView.smoothScrollToPosition(input.size()-1);
    }

    private void updateItem(int position, String content){
        input.remove(position);
        input.add(content);
        recyclerView.post(new Runnable()
        {
            @Override
            public void run() {
                mAdapter.notifyDataSetChanged();
            }
        });
        recyclerView.smoothScrollToPosition(input.size()-1);
    }


    @Override
    public void onBackPressed() {
        Intent intent = new Intent();
        setResult(RESULT_CANCELED, intent);
        finish();
    }


    public static Document protocolBytesResponse(Document doc, String content, String message, boolean status) {
        Document newDoc = new Document();
        newDoc.append("command", "FILE_BYTES_RESPONSE");
        Document sub_doc = (Document) doc.get("fileDescriptor");
        newDoc.append("fileDescriptor", sub_doc);
        newDoc.append("pathName", doc.getString("pathName"));
        newDoc.append("position", doc.getLong("position"));
        long length = doc.getLong("length");
        newDoc.append("length", length);
        newDoc.append("content", content);
        newDoc.append("message", message);
        newDoc.append("status", status);
        return newDoc;
    }

    public static Document protocolHeart() {
        Document newDoc = new Document();
        newDoc.append("command", "HEART");
        return newDoc;
    }

    /**
     * To generate a REQUEST json document of file protocol
     *
     * @return Document
     */
    public Document protocolFileSendRequest(String md5,
                                            long lastModified,
                                            long fileSize,
                                            String fileName) {
        Document doc = new Document();
        doc.append("command", "FILE_CREATE_REQUEST");
        Document sub_doc = new Document();
        sub_doc.append("md5", md5);
        sub_doc.append("lastModified", lastModified);
        sub_doc.append("fileSize", fileSize);
        doc.append("fileDescriptor", sub_doc);
        doc.append("pathName", fileName);
        return doc;
    }

    public Document inputRead(BufferedReader in) throws IOException {
        String output = "";
        try {
            while (null == (output = in.readLine())) {

            }
        } catch (IOException | ClassCastException e) {
            Log.e(TAG, "inputRead: " + e.getMessage());
            throw e;
        }
        return Document.parse(output);
    }

    public ByteBuffer readFile(String filePath, long position, long length) {
        synchronized (this) {

            try {
                File file = new File(filePath);
                Log.i(TAG, "readFile: " + file);
                RandomAccessFile raf = new RandomAccessFile(file, "rw");
                FileChannel channel = raf.getChannel();
                FileLock lock = channel.lock();
                ByteBuffer bb = ByteBuffer.allocate((int) length);
                channel.position(position);
                int read = channel.read(bb);
                lock.release();
                channel.close();
                raf.close();
                if (read < length) throw new IOException("did not read everything expected");
                return bb;
            } catch (IOException e) {
                // try another one
            }

            return null;
        }
    }
}
