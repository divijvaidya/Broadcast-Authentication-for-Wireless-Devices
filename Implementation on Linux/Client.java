import java.io.IOException;
import java.net.*;
import java.security.*;
import java.util.*;

public class Client {

	private final static boolean DEBUG = false;
	private final static boolean TEXT = true;
	private int d; //number of time intervals delayed before key is revealed
	private int intervalSize; //Time interval size
	private String initializeKey; //Key used to verify other keys
	private static int port;
	private int delta;
	private int maxTransferTime;
	private long startTime;
	private String[][] storageBuffer;
	private static int maxPacketStorage;
	private String[] verifiedMessages;
	private MulticastSocket socket;
	private InetAddress address;
	private DatagramPacket packet;
	private int packetCount;
	private int recPackets;
	private int recKeys;
	private String recMessage;
	private static String OUTPUT;

	public static int sizeOfPacket;
	public byte[] buf;

	Client()
	{
		port=4466;
		sizeOfPacket=2048;
		initializeKey="7417826589ec9380b64e8698fab941e5";
		startTime=0;
		maxPacketStorage = 20;
		packetCount=0;
		d=2;
		recPackets = 0;
		recKeys = 0;
	}
	public static void main(String args[]) throws IOException
	{
		Client c = new Client();
		c.initializeConnection();
		//----------SUMMARY----------
		OUTPUT = "No. of packets Recieved: " + c.recPackets + "\nNo. of packets Verified: " + c.packetCount + "\nNo. of Keys Recieved: " + c.recKeys + "\nMessage: \n" + c.recMessage;
		if(TEXT) System.out.println(OUTPUT);
		//---------------------------
	}

	private boolean verifyPacket(String msg, String key)
	{
		String mac=msg.substring(msg.lastIndexOf("<mac>")+5,msg.lastIndexOf("</mac>"));
		String data=msg.substring(msg.indexOf("<data>")+6,msg.lastIndexOf("</data>"));
		String calculatedMac=calculateMAC(data, key);

		if(mac.equals(calculatedMac)){
			if(DEBUG) System.out.println("Verified");
			return true;
		}
		return false;
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
		}catch(NoSuchAlgorithmException nsae){}
		return "";
	}
	private void initialiseSystem()
	{
		storageBuffer=new String[((maxTransferTime-d)/intervalSize)][maxPacketStorage];//default 10
		if(DEBUG) System.out.println("Storage Buffer Size: "+storageBuffer[0].length);

		for(int i=0;i<storageBuffer.length;i++){
			storageBuffer[i][0]="1";//Pointer to the first empty space
		}

		verifiedMessages=new String[4000];
		for(int i=0;i<verifiedMessages.length;i++){
			verifiedMessages[i]="";
		}
		verifiedMessages[0]="1";//Pointer to the first empty space

		if(DEBUG) System.out.println("System Initialized");
	}
	private void storePacket(String msg)
	{
		String ans=msg.substring(msg.lastIndexOf("<num>")+5,msg.lastIndexOf("</num>"));
		ans = ans.concat(msg.substring(msg.indexOf("<data>")+6, msg.lastIndexOf("</data>")));
		verifiedMessages[Integer.parseInt(verifiedMessages[0])]=ans;
		verifiedMessages[0]=Integer.toString(Integer.parseInt(verifiedMessages[0])+1);
	}
	private String calculateMAC(String data, String key){
		data = data + key;
		int num = data.hashCode();
		data = calculateMD5(Integer.toString(num));
		return data;
	}

	public boolean verifyKey(int interval,String key)
	{
		for(int i=0;i<interval;i++){
			key=calculateMD5(key);
		}
		if(key.equals(initializeKey))return true;
		return false;
    	}

	public void showMessage(int count)
	{
		int size=verifiedMessages.length;
		if(DEBUG) System.out.println("Size of verified: "+verifiedMessages[0]);
		if(verifiedMessages[0].equals("1"))return;

		Arrays.sort(verifiedMessages,1,count);

		String ans="";
		for(int i=1;i<=count;i++){
			String temp=verifiedMessages[i].substring(1,verifiedMessages[i].length());
			ans = ans.concat(temp);
		}
		if(DEBUG) System.out.println("Answer: "+ans);
		if(DEBUG) System.out.println(ans.length());
		recMessage = ans;
	}

	public void initializeConnection() throws IOException
	{
		try{
		    socket = new MulticastSocket(port);
		    address = InetAddress.getByName("224.0.0.0");
		    socket.joinGroup(address);
		}catch(BindException bb){
		    if(DEBUG) System.out.println(bb.getMessage());
		}

		boolean flag=false;//Used to check whether startTime has been initialzed by a packet or not

		while(true){
			long timeNow=System.currentTimeMillis();
			if(flag && (timeNow-startTime)>maxTransferTime){
				if(DEBUG) System.out.println("Time out! Loop Break");
				break;
	    		}

			buf = new byte[sizeOfPacket];
			packet = new DatagramPacket(buf, buf.length);
			if(DEBUG) System.out.println("Ready to Recieve: " + System.currentTimeMillis());
			socket.setSoTimeout(intervalSize);
			try {
				socket.receive(packet);
			} catch (java.io.InterruptedIOException iioe) {
				if(DEBUG) System.out.println("Waiting Timed Out! No more Packets to recieve. ");
				continue;
			}

			String received = new String(packet.getData(), 0, packet.getLength());
			if(DEBUG) System.out.println("Packet Recieved: " + received);
			if(DEBUG) System.out.println("Data Packet received at: "+ System.currentTimeMillis());

			received.trim();

			long now=System.currentTimeMillis();

			if(received.startsWith("<bootstrap>") && received.endsWith("</bootstrap>")){

				if(DEBUG) System.out.println("Bootstrap packet received!");

				String[] arr=received.split(":");
				if(DEBUG) System.out.println("Splitted: " + Arrays.toString(arr));

				String temp=arr[0].substring(11,arr[0].length());
				if(DEBUG) System.out.println("Temp: " + temp);
				delta=Integer.parseInt(temp);

				if(DEBUG) System.out.println("delta="+delta);

				intervalSize=Integer.parseInt(arr[1]);

				if(DEBUG) System.out.println("interval size="+intervalSize);

				maxTransferTime=Integer.parseInt(arr[2]);

				if(DEBUG) System.out.println("Max transfer time= "+maxTransferTime);
				maxTransferTime += delta;

				temp=arr[3].substring(0,arr[3].length()-12);
				startTime=Long.parseLong(temp);

				if(DEBUG) System.out.println("Start Time= "+startTime);

				flag=true;//start time has been initialized

				initialiseSystem();
			}else if(received.startsWith("<tss>") && received.endsWith("</data>") && !received.endsWith("</tsc>")){
				if(DEBUG) System.out.println("Time stamp packet arrived!");
				String time="<tsc>"+Long.toString(now)+"</tsc>";
				received = received.concat(time);
				if(DEBUG) System.out.println("Sent: "+received);
				byte[] tempbuffer = new byte[sizeOfPacket];
				tempbuffer=received.getBytes();
				DatagramPacket sendpacket= new DatagramPacket(tempbuffer, tempbuffer.length,address,port);
				socket.send(sendpacket);
			}else if(received.startsWith("<num>") && received.endsWith("</data>")){//The data packet
				int interval=(int)(now-startTime+delta)/(intervalSize);
				if(Math.floor((double)(now-startTime+delta)/(double)(intervalSize))<=(Math.floor((double)(now-startTime-delta)/(double)(intervalSize))+d)){
					storageBuffer[interval][Integer.parseInt(storageBuffer[interval][0])]=received;
					storageBuffer[interval][0]=Integer.toString(Integer.parseInt(storageBuffer[interval][0])+1);
					recPackets++;
				}else{
					if(DEBUG) System.out.println("Packet Dropped due to unusually long delay!!");
				}
			}else if(received.startsWith("<key>") && received.endsWith("</num>")){//The packet with the key
				recKeys++;
				String key=received.substring(5,received.lastIndexOf("</key>"));
				int interval=(int)(now-startTime+delta)/(intervalSize);
				int interval_key=Integer.parseInt(received.substring(received.lastIndexOf("<num>")+5,received.lastIndexOf("</num>")));
				if(DEBUG) System.out.println("Current Interval: "+interval+" Key Interval: "+interval_key);
				boolean test=verifyKey(interval_key,key);
				if(test){
					if(DEBUG) System.out.println("Key matched");
					if(DEBUG) System.out.println(Arrays.deepToString(storageBuffer));
					if(DEBUG) System.out.println("Interval Key"+interval_key);
					if(DEBUG) System.out.println("For Limit: " + Integer.parseInt(storageBuffer[interval_key][0]));
					for(int i=1;i<Integer.parseInt(storageBuffer[interval_key][0]);i++){
						if(verifyPacket(storageBuffer[interval_key][i],key)){
							if(DEBUG) System.out.println("Packet verified!!");
							packetCount++;
							storePacket(storageBuffer[interval_key][i]);
						}else{
							if(DEBUG) System.out.println("Packet Not Matched");
						}
					}
				}
	    		}
		}
		showMessage(packetCount);
		socket.close();
    	}
}
