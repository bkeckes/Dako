package edu.hm.dako.EchoApplication.ReliableUdpMultiThreaded;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Calendar;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.hm.dako.EchoApplication.Basics.AbstractClientThread;
import edu.hm.dako.EchoApplication.Basics.EchoPDU;
import edu.hm.dako.EchoApplication.Basics.SharedClientStatistics;
import edu.hm.dako.EchoApplication.ReliableUdpSocket.ReliableUdpSocket;
import edu.hm.dako.EchoApplication.ReliableUdpSocket.ReliableUdpObject;


/**
 * Klasse ReliableUdpMultiThreadedEchoClientThread
 * 
 * @author Mandl
 * 
 */
// TODO Ganze Klasse implementieren
public class ReliableUdpMultiThreadedEchoClientThread extends
		AbstractClientThread {

	private static Log log = LogFactory.getLog(ReliableUdpMultiThreadedEchoClientThread.class);
	
    // Lokaler Port zur Kommunikation mit dem Echo-Server 
    private int currentPort;
    		
    // Name des Threads
    private String threadName;
    
    // Nummer des Echo-Clients
    private int numberOfClient;
    
    // Laenge einer Nachricht
    private int messageLength;
    
    // Anzahl zu sendender Nachrichten je Client-Thread
    private int numberOfMessages;
  
    // Portnummer des Threads
    private int localPort;
    
    // Serverport 
    private int serverPort;
    
    // Adresse des Servers
    private String remoteServerAddress;
    
    // Denkzeit des Clients zwischen zwei Requests in ms
    private int clientThinkTime;
    
    // Gemeinsame Daten der Threads
    private SharedClientStatistics sharedData;
    
    // Socket-Verbindung
    private ReliableUdpSocket con;
    private ObjectInputStream in;
    private ObjectOutputStream out;
  
    // Zeitstempel f�r RTT-Berechnung und Kalender
	private long rttStartTime;
	private long rtt;
	
    /**
     * initialize
     * 
     * @param serverPort: Port des Servers
     * @param remoteServerAddress: Adresse des Servers
     * @param numberOfClient: Laufende Nummer des Test-Clients
     * @param messagelength: Laenge einer Nachricht
     * @param numberOfMessages: Anzahl zu sendender Nachrichten je Thread
     * @param clientThinkTime: Denkzeit des Test-Clients
     * @param sharedData: Gemeinsame Daten der Threads 
     */
	@Override
	public void initialize(
		int serverPort,
		String remoteServerAddress,
		int numberOfClient, 
		int messageLength, 
		int numberOfMessages,
		int clientThinkTime,
		SharedClientStatistics sharedData)
	{		
	    this.serverPort = serverPort;
	    this.remoteServerAddress = remoteServerAddress;
	    this.numberOfClient = numberOfClient;
	    this.messageLength = messageLength;
	    this.numberOfMessages = numberOfMessages;
	    this.clientThinkTime = clientThinkTime;
	    this.sharedData = sharedData;
		this.setName("EchoClient-".concat(String.valueOf(numberOfClient+1)));    
		threadName = getName();
	    
		// Verbindung zum Server aufbauen
		try {
			System.out.println(this.getName()+": ReliableUdpSocket erstellen ("+remoteServerAddress+", "+serverPort+")");
			con = new ReliableUdpSocket(this.remoteServerAddress, this.serverPort);
		    out = new ObjectOutputStream(con.getOutputStream());
		    in = new ObjectInputStream(con.getInputStream());
		    localPort = con.getLocalPort();
		    currentPort = con.getPort();
		    log.debug(threadName + ": Verbindung zum Server aufgebaut mit Port " + localPort);
		    log.debug(threadName + ": Registrierter Port: " + currentPort);    	
		    System.out.println("Verbindung mit LocalPort: "+localPort+" und currentPort: "+currentPort+" am serverPort: "+serverPort);
        } 
	    catch (Exception e) 
        {         
	    	System.out.println("keine verbindung zum server");
            log.debug("Exception beim Verbindungsaufbau: " + e);
        }
	}

	/**
	 * Run-Methode fuer den Test-Thread: 
	 * Client-Thread sendet hier alle Requests und wartet auf Antworten
	 */
	public void run() 
	{   
		sharedData.incrNumberOfLoggedInClients();
		
        /**
         * Synchronisation mit allen anderen Client-Threads:
         * Warten, bis alle Clients angemeldet sind und dann
         * erst mit der Lasterzeugung beginnen
         */
        while (!sharedData.allClientsLoggedIn())
       	{
        	try {     
		        Thread.sleep(100);
        	}
	        catch (InterruptedException e) {
	        	log.error("Sleep unterbrochen");
	        }
        }
        
        for (int i = 0; i < numberOfMessages; i++) {
			// RTT-Startzeit ermitteln
			rttStartTime = System.nanoTime();
			
			try {
				
				EchoPDU echoSend = new EchoPDU();
				echoSend.setClientName(this.getName());
				echoSend.setMessage("DASISTDIEMESSAGE:"+(i+1));
				// Letzter Request?
				if (i == numberOfMessages - 1) {
					echoSend.setLastRequest(true);
				}
				
				
				
				//Message wird gesendet
				//out.writeObject(echoSend);
				//out.flush();
				out.writeObject(echoSend);
				/*
				ReliableUdpObject obj = new ReliableUdpObject();
				obj.setData(echoSend);
				obj.setId(numberOfClient);
				con.sendIt(InetAddress.getByName(remoteServerAddress), serverPort, obj);
				*/
			}
			catch (IOException e1) {		
				e1.printStackTrace();
			} 
			
			
			try{
				// Antwort entgegennehmen
				EchoPDU echoRec = (EchoPDU) in.readObject();
				//System.out.println("Client "+this.getName()+": "+echoRec.getMessage()+" von "+echoRec.getServerThreadName());
				// RTT berechnen
				rtt = System.nanoTime() - rttStartTime;
				// Response-Zaehler erhoehen
				sharedData.incrSentMsgCounter(numberOfClient);
				sharedData.incrReceivedMsgCounter(numberOfClient, rtt, echoRec.getServerTime());
			}
			catch (IOException e1) {	
				e1.printStackTrace();
			} catch (ClassNotFoundException e) {
				System.out.println("Fehler in Typkonvertierung");
				e.printStackTrace();
			} 
			
			//Wartezeit
			try {
				Thread.sleep(clientThinkTime);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}	
			
		}
		  
        try {
        	// Transportverbindung abbauen
			out.close();
			in.close();
			con.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}	

}