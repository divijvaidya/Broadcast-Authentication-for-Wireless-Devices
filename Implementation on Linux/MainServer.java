import java.security.*;
import java.net.*;
import java.io.*;

public class MainServer
{
	Thread KeyThread;
	MulticastSocket socket;

	/* CONSTANTS AND CLASS VARIABLES */
	private static final boolean DEBUG = false;
	private static final boolean TEXT = true;
	private static final int MAX_KEYS = 1000;
	private static final int MAX_CLIENTS = 100;
	private static final int PACKETS_PER_INTERVAL =3;
	private static final String INIT_KEY = "&$(HK*#FR&)$(*awrq&gSe^^g$s^ASdfg$&g#sASgdfrFwgwg*@$)";
	private static final int DELAY = 2; //Delay after which Keys have to be disclosed.
	private static final int PORT_NUMBER = 4466;
	private static final String INET_ADDRESS = "224.0.0.0";
	private String[] Keys;
	private long[] RTT;
	private boolean Synchro;
	private long DELTA;
	private long TINT;
	private long TSTART;
	private int NUM_CLIENTS;
	private String INPUT;
	private int NUM_PACKETS;
	private int NUM_KEYS;
	private String[] PACKETS;
	

	public static void main(String args[]) throws IOException
	{
		if(TEXT) System.out.println("Welcome to uTesla Server v0.1!");
		MainServer ms = new MainServer();
		if(TEXT) System.out.println("Message Sent. Bbye! :)");
	}
	private String calculateMD5(String data)
	{
		byte[] defaultBytes = data.getBytes();
		try{
			MessageDigest algorithm = MessageDigest.getInstance("MD5");
			algorithm.reset();
			algorithm.update(defaultBytes);
			byte messageDigest[] = algorithm.digest();
			StringBuffer hexString = new StringBuffer();
			for (int i=0;i<messageDigest.length;i++) {
				hexString.append(Integer.toHexString(0xFF & messageDigest[i]));
			}
			return hexString.toString();
		}catch(NoSuchAlgorithmException nsae){
		}
		return "";
	}
	private long max(long ARR[])
	{
		long maxnum = ARR[0];
		for(int i=0; i<NUM_CLIENTS; i++) {
			if(ARR[i] > maxnum) {
				maxnum = ARR[i];
			}
		}
		return maxnum;
	}
	private String calculateMAC(String data, String key)
	{
		data = data + key;
		int num = data.hashCode();
		data = calculateMD5(Integer.toString(num));
		return data;
	}
	public MainServer() throws IOException
	{
		//Initialize
		NUM_CLIENTS = 0;
		Synchro = false;
		INPUT = null;
		NUM_PACKETS = 0;
		INPUT = "";

		//Step-1: Generate 1000 keys using Md5
		if(DEBUG) System.out.println("Step-1: Generating Keys...");
		Keys = new String[MAX_KEYS];
		Keys[MAX_KEYS-1] = calculateMD5(INIT_KEY);
		for(int i=MAX_KEYS-2; i>=0; i--) {
			Keys[i] = calculateMD5(Keys[i+1]);
		}
		if(DEBUG) System.out.println("1000 Keys Generated");
		if(DEBUG) System.out.println(Keys[0]);
		//Done-1
		
		//Step-2: Time Synchronization
		if(DEBUG) System.out.println("Step-2: Time Synchronization");
		TimeServer ts = new TimeServer();
		ts.run();
		socket.close();
		DELTA = max(RTT);
		TINT = DELTA * (PACKETS_PER_INTERVAL + 1);
		if(DEBUG) System.out.println("Time Synchro done!");
		if(DEBUG) System.out.println("DELTA: " + DELTA);
		if(DEBUG) System.out.println("TINT : " + TINT);
		if(DEBUG) System.out.println("Number of Clients: " + NUM_CLIENTS);
		//Done-2

		//Step-3: Take Input from User
		if(DEBUG) System.out.println("Step-3: Taking User Input");
		try{
			FileInputStream fstream = new FileInputStream("ip_test.txt");
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String strLine;
			while ((strLine = br.readLine()) != null)   {
				INPUT += strLine;
			}
			in.close();
		}catch (Exception e){//Catch exception if any
		  System.err.println("Error: " + e.getMessage());
		}
		if(DEBUG) System.out.println("INPUT Taken from user. Input Length = " + INPUT.length());
		//Done-3

		//Step-4: Divide string into Strings of 1000 chars each
		if(DEBUG) System.out.println("Step-4: Dividing String and preparing packets");
		PACKETS = INPUT.split("(?<=\\G.{1000})");
		NUM_PACKETS = PACKETS.length;
		if(DEBUG) System.out.println("Number of Packets: ");
		//Preparing the packets now
		int j = -1;
		for(int i=0; i<NUM_PACKETS; i++) {
			if(i%3==0) j++;
			PACKETS[i] = "<num>" + Integer.toString(i+1) + "</num><mac>" + calculateMAC(PACKETS[i],Keys[j]) + "</mac><data>" + PACKETS[i] + "</data>";
		}
		NUM_KEYS = j+1;
		if(DEBUG) System.out.println("Packets Prepared!");
		if(DEBUG) System.out.println("Number of Keys used: " + NUM_KEYS);
		//Done-4

		//Step-5: Broadcast Bootstrapping Info
		if(DEBUG) System.out.println("Step-5: Bootstrapping the recievers");
		TSTART = System.currentTimeMillis() + 500L;
		if(DEBUG) System.out.println("TSTART: "+TSTART);
		int port = PORT_NUMBER;
		String address = INET_ADDRESS;
		long maxtime = ((long)NUM_PACKETS/3L +1L + (long)DELAY)*TINT;
		if(DEBUG) System.out.println("Total Transfer Time: "+maxtime);
		String packet = "<bootstrap>" + Long.toString(DELTA) + ":" + Long.toString(TINT) + ":" + Long.toString(maxtime) + ":" + Long.toString(TSTART) + "</bootstrap>";
		if(DEBUG) System.out.println("Bootstrapping Packet: " + packet);
		try{
			socket = new MulticastSocket(port);
			InetAddress add = InetAddress.getByName(address);
			socket.joinGroup(add);
			byte buffer[] = packet.getBytes();
			DatagramPacket dpacket = new DatagramPacket(buffer, buffer.length, add, port);
			socket.send(dpacket);
			socket.close();
		} catch(java.net.BindException b){}
		if(DEBUG) System.out.println("Bootstrapping Done!");
		//Done-5

		//Step-6: Start sending Data and Keys in the two threads
			//Configure the connection here to avoid conflict
		if(DEBUG) System.out.println("Step-6: Sending Data and Keys");
		try{
			socket = new MulticastSocket(port);
			InetAddress add = InetAddress.getByName(address);
			socket.joinGroup(add);
			DataServer ds = new DataServer(add);
			ds.run();
		} catch (java.net.BindException b) {
			System.out.println("Error: " + b.getMessage());
		}
		if(DEBUG) System.out.println("Execution finished. Current Time: " + System.currentTimeMillis());
	}
	public class DataServer { 
		InetAddress address;
		DataServer(InetAddress add){
			address = add;
		}
		public void run(){
			long TS = TSTART;
			long T = TS + TINT;
			int PKT = 0;
			long KTS = TSTART + (DELAY*TINT);
			long KT = KTS + TINT;
			int NKEYS = 0;
			int count = 0;
			String tmp;
			while(NKEYS < NUM_KEYS) {
				if(System.currentTimeMillis() < TS) {
					if(DEBUG) System.out.println("Waiting...");
				}
				while(System.currentTimeMillis() < TS) {;}
				if(DEBUG) System.out.println("Now, Time: " + System.currentTimeMillis());
				if(System.currentTimeMillis() >= TS && System.currentTimeMillis() < T) {
					if(PKT < NUM_PACKETS) {
						byte buff[];
						DatagramPacket dpack;
						for(int i=0; i<PACKETS_PER_INTERVAL; i++) {
							if(PKT >= NUM_PACKETS) {
								break;
							}
							buff = PACKETS[PKT].getBytes();
							dpack = new DatagramPacket(buff, buff.length, address, PORT_NUMBER);
							try {
								socket.send(dpack);
							} catch(java.io.IOException ioe) {
								if(DEBUG) System.out.println(ioe.getMessage());
							}
							PKT++;
							if(DEBUG) System.out.println("Sending packets at: "+System.currentTimeMillis());
						}
					}
					if(count >= DELAY) {
						while(System.currentTimeMillis() < (TS + (TINT/2))) {; }
						byte buff[];
						DatagramPacket dpackk;
						tmp = "<key>" + Keys[NKEYS] + "</key><num>" + NKEYS + "</num>";
						if(DEBUG) System.out.println("Sending Key: " + tmp + " at time: " + System.currentTimeMillis());
						buff = tmp.getBytes();
						dpackk = new DatagramPacket(buff, buff.length, address, PORT_NUMBER);
						try {
							socket.send(dpackk);
						} catch(java.io.IOException ioe) {
							if(DEBUG) System.out.println(ioe.getMessage());
						}
						NKEYS++;
					}
					TS = T;
					T = TS + TINT;
					if(DEBUG) System.out.println("New TS: "+TS);
					count++;
				}
			}
		}
	}
	public class TimeServer { 
		InetAddress address;
		public void run(){
			try{
				byte[] buffer = new byte[2048];
				int port = PORT_NUMBER;
				String address1 = INET_ADDRESS;
				try{
					socket = new MulticastSocket(port);
					InetAddress add = InetAddress.getByName(address1);
					socket.joinGroup(add);
					String mess = "<tss>" + Long.toString(System.currentTimeMillis()) + "</tss>";
					String rdata = "";
					for(int i=0; i < 1050; i++) {
						rdata += "a";
					}
					mess = mess + "<data>" + rdata + "</data>";
					byte message[] = mess.getBytes();
					DatagramPacket packet = new DatagramPacket(message, message.length, add, port);
					socket.send(packet);
					socket.receive(packet);
					RTT = new long[MAX_CLIENTS];
					socket.setSoTimeout(1000);
					String tmp;
					try {
						for(int i=0; i<MAX_CLIENTS; i++) {
							DatagramPacket packet2 = new DatagramPacket(buffer, buffer.length, add, port);
							socket.receive(packet2);
							tmp = new String(buffer);
							if(!tmp.startsWith("<tss>")) {
								//Discard Packet
								continue;
							}
							if(DEBUG) System.out.println("TimeSynchroPacket Recieved: " + tmp);
							long TS = Long.parseLong(tmp.substring(tmp.indexOf("<tss>")+5, tmp.indexOf("</tss>")));
							long TR = Long.parseLong(tmp.substring(tmp.indexOf("<tsc>")+5, tmp.indexOf("</tsc>")));
							RTT[i] = TR-TS;
							if(DEBUG) System.out.println("TR,TS: " + TR + ", " + TS);
							NUM_CLIENTS++;
						}
					} catch(java.io.InterruptedIOException ie) {
						socket.close();
						Synchro = true;
						return;
					}
					socket.close();
				}
				catch(java.net.BindException b){}
			}
			catch (IOException e){
				System.err.println(e);
			}
		}
	}
}
