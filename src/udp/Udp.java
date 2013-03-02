/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package udp;

/**
 *
 * @author Enos
 */
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;

public class Udp {
    
    private InetAddress host;
    private int port = 55556;
    private final int serverPort = 55555;
    private DatagramSocket socket;
    private DatagramPacket packet;
    public File file;
    public int fileSize;
    public int sizeLeft, nChunks = 0, numberOfPackets;;
    public final int chunkSize = 512;
    public int windowSize = 42;
    public FileInputStream fis;
    public byte[][] windowBuffer;
    public String fileName;
	//constructor
    public Udp()
    {
        try{
            socket = new DatagramSocket();
        }catch(SocketException e){
            System.out.print(e.getMessage());
        }
    }
/*
    //opens file to send (prototype) ?? fileChooser is more reliable
*/
    
    public boolean connect(String host){
        try {
            this.host = InetAddress.getByName(host);
            return true;
        } catch (UnknownHostException ex) {
//            Logger.getLogger(Udp.class.getName()).log(Level.SEVERE, null, ex);
            System.out.println(ex.getMessage());
            return false;
        }
    }

    //this sends a request to the server. 3 packets, 1-filename, 2-filesize in bytes, 3-window size
    //after sending the 3, it then waits for an answer from the server. i was thinking it'd be an integer, 
    //1-for YES, 0-for NO. haven't yet put that into the code
    //also in case the server is down, i want it to try like 3 times with an interval of about 1 second between each attempt
    //and if there is no reply, it tells me the server is down.
    //problem is that the socket.receive() method is a blocking one and will wait till it gets something
    //so we'll have to find a way to kill it after 1second without a response then try agian, on the third fail
    //inform the user that the server is down. might need another thread to track the time
    public int request() throws IOException{
        socket.setSoTimeout(10000);
        byte[] buffer;
        buffer = fileName.getBytes();       
        packet = new DatagramPacket(buffer, buffer.length, this.host, this.serverPort);
        socket.send(packet);
        buffer = seqNo(fileSize,buffer);
        packet = new DatagramPacket(buffer, buffer.length, this.host, this.serverPort);
        socket.send(packet);
        buffer = seqNo(windowSize,buffer);
        packet = new DatagramPacket(buffer, buffer.length, this.host, this.serverPort);
        socket.send(packet);
        try{
            socket.receive(packet);
            int reply = seqNo(packet.getData());
            return reply;
        } catch(Exception e){
            String message = "Program is not running on Server";
            JOptionPane.showMessageDialog(null, message);
            return -1;
        }
//        return true;
    }
    
    //this one sends the file 1 window at a time. e.g if the file splits into 6 packets, and we're using a window of 2, it
    //will send the file, 2 packets at a time til it finishes. 
/*    public void sendFile() throws IOException{
        if(request()){
            socket.setSoTimeout(0);
            int n;
            int packetsLeft;
            if(fileSize%chunkSize != 0){
                  numberOfPackets = fileSize/chunkSize;
                  numberOfPackets++;
              }
              else{
                  numberOfPackets = fileSize/chunkSize;
              }

            if(numberOfPackets%windowSize != 0){
                n = numberOfPackets/windowSize;
                n++;
            }
            else{
                n = numberOfPackets/windowSize;
            } //n will represent the number of windows we'll have to send to transfer the whole file
            packetsLeft = numberOfPackets;
            while(n > 0){
                if(packetsLeft > windowSize){
                    createWindow(this.windowSize);
                    packetsLeft -= windowSize;
                } else{
                    createWindow(packetsLeft);
                }
                sendWindow(this.windowBuffer);
                n--;
/*                try {
                    Thread.sleep(100L);  //i forgot why I added this delay.
                } catch (InterruptedException ex) {
                    Logger.getLogger(Udp.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
     
    }
*/
//this creates a transfer window of size windowSize which we shall use to create and send our packets
//Also this method helps us easily find packets that the server needs resent(in case it didn't get them the first time round) 
    public void createWindow(int n) throws IOException{
        this.windowBuffer = new byte[n][];
        int read, i, pkt = chunkSize;

        for(i = 0; i < n; i++){
            if(sizeLeft < chunkSize) {
                pkt = sizeLeft;
            }
            windowBuffer[i] = new byte[pkt];
            read = fis.read(windowBuffer[i], 0, pkt);
            sizeLeft -= read;
            assert(read == windowBuffer[i].length);
            this.nChunks++;
        }
        
    }

//sends all the packets in the current window. then calls readAck to process the acknowledement packet from the server
    public int sendWindow(byte[][] winBuffer) throws IOException{
        byte[] buffer;
        int j = 0;
        for(int i = 0; i < winBuffer.length;i++){
            j+=winBuffer[i].length;
            buffer = seqNo(i,winBuffer[i]);
//            System.out.println("Send" + i);
            packet = new DatagramPacket(buffer, buffer.length, this.host, this.port);
            System.out.print(packet.getData().length);
            socket.send(packet);
        }
        readAck(winBuffer);
        return j;
    }
    
    //called after sending each window to process the acknowledgement packet
    //if some packets were lost during transmission, it resends them, then calls itself to 
    //check the ack packet again. recurses until no more lost packets in current window.
    public void readAck(byte[][] winBuffer) throws IOException{
        socket.receive(packet);
        byte[] buffer = packet.getData();
        byte[] buff;
        try{
            ByteArrayInputStream bais=new ByteArrayInputStream(buffer);
            int no;
            DataInputStream dais = new DataInputStream(bais);
            no = dais.readInt(); //the first int in the ack packet represents the number of packets to be resent
            System.out.println(no);
            if(no == 0) {   //if it's 1, no need to resend, so we proceed to next window
                return;
            }
            int[] resend = new int[no]; 
            for(int i = 0; i < no; i++){ 
                resend[i] = dais.readInt();  //reads the seqNo of lost packes and uses it to identify them from the window and resend them
            }
            int j = 0;
            for(int i = 0; i < winBuffer.length;i++){
                if(i == resend[j]){    //resends only the packets that were lost from the current window
                    buff = seqNo(i,winBuffer[i]);
                    System.out.println("resend");
                    packet = new DatagramPacket(buff, buff.length, this.host, this.port);
                    socket.send(packet);
                    j++;
                }                
            }
        } catch(Exception e){
            System.out.print(e.getMessage());
        }
        readAck(winBuffer); //call same function to read ack for resent packets.(recursive)
    }
//this pushes a sequence number into the buffer   
    private static byte[] seqNo(int n, byte[] buffer) throws IOException{
        byte[] seq;     
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream daos = new DataOutputStream(baos);
            daos.writeInt(n);
            daos.write(buffer);
            daos.flush();
        
        seq = baos.toByteArray();
        return seq;
    }
    
    public static int seqNo(byte[] buffer){
        try{
            ByteArrayInputStream bais=new ByteArrayInputStream(buffer);
            int i;
            try (DataInputStream dais = new DataInputStream(bais)) {
                i = dais.readInt();
            }
            return i;
        } catch(Exception e){
            System.out.print(e.getMessage());
        }
        return -1;
    }
/*    public static void main(String[] args) throws IOException{
        
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new mainJFrame().setVisible(true);
            }
        });

    }*/
}
