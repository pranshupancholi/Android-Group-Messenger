package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.PriorityQueue;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {
    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static ArrayList<String> REMOTE_PORT = new ArrayList<String>(Arrays.asList("11108", "11112", "11116", "11120", "11124"));
    static final int SERVER_PORT = 10000;

    static final String INITIAL = "INITIAL";
    static final String PROPSED = "PROPSED";
    static final String AGREED = "AGREED";

    boolean agreement = false;
    static final String separator ="#";
    int seqNo = 0;
    int msgNo=1;
    int seqCnt=1;
    private String myPort=null;

    HashMap<String,Integer> trackPropsl= new HashMap<String, Integer>();
    HashMap<String, ArrayList<Double>> proposalList= new HashMap<String,ArrayList<Double>>();
    PriorityQueue<Message> msgQ= new PriorityQueue<Message>(25,new MsgCompare());

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e(TAG, "Server Socket IOException for port "+ myPort);
            return;
        } catch (Exception e){
            Log.e(TAG, "Server Socket Exception for port "+ myPort);
        }

        final EditText msgBox = (EditText) findViewById(R.id.editText1);
        final Button sendBtn = (Button) findViewById(R.id.button4);
        sendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String msg = msgBox.getText().toString();
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort, String.valueOf(msgNo));
                StringBuilder propNo = new StringBuilder();
                propNo.append(myPort);
                propNo.append("@");
                propNo.append(msgNo);

                trackPropsl.put(propNo.toString(),0);
                proposalList.put(propNo.toString(),new ArrayList<Double>());
                msgNo++;
                msgBox.setText(" ");
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            StringBuilder finalMsg = new StringBuilder();
            Socket socket = null;
            try {
                while(true) {
                    finalMsg.setLength(0);
                    socket = serverSocket.accept();
                    BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    String msgReceived = new String(br.readLine());
                    //Log.d("Server Msg Received: ",msgReceived);
                    socket.close();
                    String[] msgs= msgReceived.split(separator);

                    String msgType = msgs[0];
                    String msgId = msgs[1];    // port

                    if(msgType.equals(INITIAL)) {  // (port@msgCount)
                        String msgData = msgs[2];
                        finalMsg.append(msgType);finalMsg.append(separator);
                        finalMsg.append(msgId);finalMsg.append(separator);finalMsg.append(msgData);
                        invokeNewClientTask(finalMsg.toString());
                    } else if(msgType.equals(PROPSED)) {
                        ArrayList<Double> tempProposalList = proposalList.get(msgId);
                        if (trackPropsl.get(msgId) == 4) {
                            Double agreedSeq = Collections.max(tempProposalList);
                            finalMsg.append(msgType);finalMsg.append(separator);finalMsg.append(msgId);
                            finalMsg.append(separator);finalMsg.append(String.valueOf(agreedSeq));
                            invokeNewClientTask(finalMsg.toString());
                        }
                    }
                }
            } catch(SocketTimeoutException ex){
                Log.e(TAG, "Server Socket Timeout" + ex.getMessage());
            } catch(SocketException ex){
                Log.e(TAG, "Server Socket Exception" + ex.getMessage());
            } catch (IOException ex) {
                Log.e(TAG, "Server Task IO Exception" + ex.getMessage());
            }
            return null;
        }

        protected void invokeNewClientTask(String msg){
            //Log.d("Call Client Msg Rcv: ", msg);
            String[] msgs = msg.split(separator);
            String msgId = msgs[1];      // msgId ---> port@msgcounter  // msgs[2] ---> agreedSeq
            if(msgs[0].equals(INITIAL))
                new PropClientTask().execute(msgId, msgs[2]);
            else if(msgs[0].equals(PROPSED)) {
                agreement = true;
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgId, msgs[2]);
            }
        }
    }

    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            String portNo = null;
            try {
                StringBuilder msgToSend = new StringBuilder();
                if(!agreement) {
                    msgToSend.append(INITIAL);msgToSend.append(separator);
                    msgToSend.append(msgs[1]);msgToSend.append("@");
                    msgToSend.append(msgs[2]);msgToSend.append(separator);msgToSend.append(msgs[0]);
                    //Log.d("Client Task Msg Send: ", msgToSend.toString());
                } else {
                    msgToSend.append(AGREED);msgToSend.append(separator);
                    msgToSend.append(msgs[0]);msgToSend.append(separator);msgToSend.append(msgs[1]);
                    //Log.d("Agree CT Msg Send: ", msgToSend.toString());
                    agreement = false;
                }
                int i = 0;
                while(i < 5) {
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(REMOTE_PORT.get(i)));
                    portNo= REMOTE_PORT.get(i);
                    socket.setSoTimeout(500);
                    socket.getOutputStream().write(msgToSend.toString().getBytes("UTF-8"));
                    socket.close();
                    i++;
                }
            } catch(SocketTimeoutException ex){
                Log.e(TAG, "Client Socket Timeout "+ex.getMessage());
                removeFailedClient(portNo);
            } catch(SocketException ex){
                Log.e(TAG, "Client Socket Exception "+ex.getMessage());
            } catch (IOException ex) {
                Log.e(TAG, "Client Task IO Exception "+ex.getMessage());
            }
            return null;
        }
    }

    private class PropClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            String senderPort=null;
            try {
                if(myPort.length() == 4)
                    myPort += "0";
                Message msgAgreed= new Message(Double.parseDouble(seqCnt + "." + myPort),msgs[0],msgs[1]);
                msgQ.add(msgAgreed);

                PriorityQueue<Message> dummyMsgQueue = new PriorityQueue<Message>(msgQ);
                seqNo=0;
                Message dumMsg = dummyMsgQueue.poll();
                while(dumMsg != null) {
                    Uri uri=Uri.parse("content://edu.buffalo.cse.cse486586.groupmessenger2.provider");
                    ContentValues keyValueToInsert = new ContentValues();
                    keyValueToInsert.put("key", Integer.toString(seqNo++));
                    keyValueToInsert.put("value", dumMsg.getMsgData());
                    getContentResolver().insert(uri, keyValueToInsert);
                    dumMsg = dummyMsgQueue.poll();
                }
                senderPort= msgs[0].split("@")[0];
                StringBuilder msgToSend = new StringBuilder();
                msgToSend.append(PROPSED);msgToSend.append(separator);
                msgToSend.append(msgs[0]);msgToSend.append(separator);msgToSend.append(seqCnt);
                msgToSend.append(".");msgToSend.append(myPort);
                seqCnt++;
                //Log.d("Prop CT Msg Send: ", msgToSend.toString());
                Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(senderPort));
                socket.setSoTimeout(500);
                socket.getOutputStream().write(msgToSend.toString().getBytes("UTF-8"));
                socket.close();
            } catch(SocketTimeoutException ex){
                Log.e(TAG, "Proposal Client Socket Timeout "+ex.getMessage());
                removeFailedClient(senderPort);
            } catch(SocketException ex){
                Log.e(TAG, "Proposal Client Socket Exception "+ex.getMessage());
            } catch (IOException ex) {
                Log.e(TAG, "Proposal Client Task IO Exception "+ex.getMessage());
            }
            return null;
        }
    }

    private synchronized void removeFailedClient(String failurePort){

        if(REMOTE_PORT.contains(failurePort)){
            REMOTE_PORT.remove(failurePort);
            for(String msgId:trackPropsl.keySet()){
                int ports=0;
                ArrayList<Double> propList= proposalList.get(msgId);
                for(Double seq: propList) {
                    String thisPort = seq.toString().split("\\.")[1];
                    if(thisPort != failurePort)
                        ports++;
                    else
                        break;
                }
                if(ports==REMOTE_PORT.size()){
                    Double agreedSeq = Collections.max(propList);
                    StringBuilder finalMsg = new StringBuilder();
                    finalMsg.append(PROPSED);finalMsg.append(separator);finalMsg.append(msgId);
                    finalMsg.append(separator);finalMsg.append(String.valueOf(agreedSeq));
                    new ServerTask().invokeNewClientTask(finalMsg.toString());
                }
            }
        }
    }

    public class MsgCompare implements Comparator<Message> {

        @Override
        public int compare(Message a, Message b) {
            try {
                int aPort = Integer.parseInt(a.getMsgId().split("@")[0]);
                int aCount = Integer.parseInt(a.getMsgId().split("@")[1]);
                int bPort = Integer.parseInt(b.getMsgId().split("@")[0]);
                int bCount = Integer.parseInt(b.getMsgId().split("@")[1]);

                if (aPort > bPort)
                    return 1;
                else if (aPort < bPort)
                    return -1;
                else if (aCount > bCount)
                    return 1;
                else if (aCount < bCount)
                    return -1;

                return 0;
            } catch(Exception e){
                return 0;
            }
        }
    }

    public class Message implements Comparable<Message> {

        double seqNo;
        String msgId;
        String msgData;

        public Message(double seqNo, String msgId, String msgData) {
            this.seqNo = seqNo;
            this.msgId = msgId;
            this.msgData = msgData;
        }

        public String getMsgData() {
            return msgData;
        }

        public String getMsgId() {
            return msgId;
        }

        @Override
        public int compareTo(Message that) {
            return 0;
        }
    }
}