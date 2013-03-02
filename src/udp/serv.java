package udp;

import java.io.*;
import java.net.*;
import java.util.ArrayList;

public class serv{
    private final int port = 55556;
    private final int serverPort = 55555;
    public final int chunkSize = 512;
    private int packets = 0, clientPort;
    private byte[] buffer, data, buff;
    private DatagramSocket datagramSocket, requestSocket;
    private DatagramPacket packet;
    private DatagramPacket ack;
    private InetAddress client;
    private File file;
    private FileOutputStream fop;
    
    public serv(){
        try {
            this.requestSocket = new DatagramSocket(serverPort);
            this.datagramSocket = new DatagramSocket(port);
        } catch (SocketException ex) {
            
        }
        buff = new byte[516];
        buffer = new byte[chunkSize];
        ack = new DatagramPacket(buffer, buffer.length);
        packet = new DatagramPacket(buff, buff.length);
    }
    
    public void serve() throws IOException{
        listen();
    }
    
    public String[] listen() throws IOException{
        System.out.println("Listening...");
        requestSocket.receive(packet);
        client = packet.getAddress();
        clientPort = packet.getPort();
        String fileName = new String(packet.getData(), 0, packet.getLength());
        requestSocket.receive(packet);
        int fileSize = seqNo(packet.getData());
        System.out.println(packet.getData().length);
        requestSocket.receive(packet);

        int windowSize = seqNo(packet.getData());
        float size = (float)fileSize/1024;
        System.out.println("File Name: " + fileName + "\nFile Size: " + size + " KB\nWindow Size: " + windowSize + "\n");        
        String[] d = new String[3];
        d[0] = fileName;
        d[1] = Float.toString(fileSize);
        d[2] = Integer.toString(windowSize);
        return d;
    }
    
    public void sendAck(byte[] buff) throws IOException{
        ack = new DatagramPacket(buff, buff.length, client, clientPort);
        datagramSocket.send(ack);
    }
    
    public void receive(String fileName, int fileSize, int windowSize) throws IOException{
        file = new File(fileName);
        fop = new FileOutputStream(file);
        if (!file.exists()) {
            file.createNewFile();
	}
        int numberOfPackets,windows,rem;
        rem = fileSize%chunkSize;
        if(rem != 0){
                  numberOfPackets = fileSize/512;
                  numberOfPackets++;
        }else{
                  numberOfPackets = fileSize/512;
        }
        rem = numberOfPackets%windowSize;
        if(rem != 0){
                windows = numberOfPackets/windowSize;
                windows++;
            }
            else{
                windows = numberOfPackets/windowSize;
                rem = windowSize;
            }
        datagramSocket.setSoTimeout(3000);
        while(windows > 0){
            if(windows == 1) {
                windowSize = rem;
            }
            ArrayList<pkt> myArr = new ArrayList<>(windowSize); //array of packets in current window. i'm using this to hold all the packets in the window. i only write to file when the window has been completely sent
            ArrayList<Integer> myArr2 = new ArrayList<>(windowSize); 
            
            receiveWindow(windowSize, myArr2, myArr);
            
            pkt c;
            for(int k = 0; k < windowSize; k++){
                for(int j = 0; j < windowSize; j++){
                    c = myArr.get(j);
                    if(k == c.seqNo){
                        fop.write(c.data,0,c.data.length);
                        break;
                    }

                }
            }
            System.out.println("Window: " + windows);
            windows--;
            int[] m = new int[1];
            m[0] = 0;
            byte[] buff = resend(m);
            ack = new DatagramPacket(buff, buff.length, client, clientPort);
            datagramSocket.send(ack);
                        
        }
        System.out.print(packets + " packets received" + "\n\nTransfer complete!");
        fop.close();
    }
    
    public int receiveWindow(int windowSize, ArrayList<Integer> myArr2, ArrayList<pkt> myArr) throws IOException{
        int n = 0;
        while(n < windowSize){
            try{
                datagramSocket.receive(packet);
                System.out.print(packet.getData().length);
            } catch(SocketTimeoutException e){ //this is run if the receive() method timesout
                System.out.println((windowSize - myArr.size()) + " packet(s) lost!");
                int[] m = missing(myArr2, windowSize);

                byte[] buff = resend(m);  //figure out the missing packets and ask for them to be resent
                ack = new DatagramPacket(buff, buff.length, client, clientPort);
                datagramSocket.send(ack);
                continue;
            }
            buffer = packet.getData();               
            data = new byte[packet.getLength() - 4];               
            int pos = seqNo(packet.getData()); 
            
            myArr2.add(pos);                
            for(int j = 0;j<data.length;j++){
                data[j] = buffer[j+4]; //push the data from the received packet into it's own buffer 
            }
            
            myArr.add(new pkt(pos,data)); //add each seqNo received to this list. I use it in missing() to figure out which packets didn't arrive

//            System.out.println("Packet No. " + pos + " received\nn: " + n);
            n++;
            packets++;
        }
        return packets;
    }
    
    
    //this extracts and returns the sequence number from the packet received
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

    //from the arraylist of received integers, we figur out which ones are missing by their sequence number
    //and place them in an int array. index 0 holds the total number of missing packets
    public static int[] missing(ArrayList<Integer> recv, int windowSize){
        int i = windowSize - recv.size();
        int j = 1;
        int[] m = new int[i+1];
        for(i=0;i<windowSize;i++){
            if(recv.contains(i)){
                continue;
            }
            m[j] = i; j++;
        }
        m[0] = j-1;
        return m;
    }

    //follows logically after missing() above. it packs the int array generated into a byte array to be sent to the 
    //client.
    public byte[] resend(int[] n) throws IOException{
        byte[] seq;     
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream daos = new DataOutputStream(baos)) {
            for(int i = 0; i < n.length; i++){
                daos.writeInt(n[i]);
            }
            daos.flush();
        }
        seq = baos.toByteArray();
        return seq;
    }
      
}

class pkt{
    public int seqNo;
    public byte[] data;
    pkt(int seqNo, byte[] data){
        this.seqNo = seqNo;
        this.data = data;
    }
}