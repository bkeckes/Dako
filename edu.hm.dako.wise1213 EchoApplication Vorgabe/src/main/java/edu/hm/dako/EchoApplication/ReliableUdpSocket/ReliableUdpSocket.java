package edu.hm.dako.EchoApplication.ReliableUdpSocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.hm.dako.EchoApplication.Basics.EchoPDU;

/**
 * Klasse ReliableUDPSocket
 * 
 * Entspricht einer stark vereinfachten TCP Socket-Implementierung. Es werden
 * die gleichen Methoden bereitgestellt. Als Sicherungsmassnahme wird ein
 * Stop-And-Go Verfahren mit einer 3-maligen Sendewiederholung,sowie
 * Duplikaterkennung realisiert.
 * 
 * @author Weiss
 * 
 * @version 1.0.1
 */
public class ReliableUdpSocket {

	private static Log log = LogFactory.getLog(ReliableUdpSocket.class);

	/*
	 * Verschiedene Threads fuer die Bearbeitung von ankommenden Nachrichten und
	 * zum Senden von Nachrichten.
	 */

	/**
	 * Thread zum Weiterleiten der Daten aus der Data Queue an die darueber
	 * liegende Schicht
	 */
	public class ForwardDataThread extends Thread {
		public ForwardDataThread() {
			setName("ForwardDataThread: " + getLocalPort());
		}

		public void run() {
			try {
				outputStreamAnDieObereSchicht = new ObjectOutputStream(pipedOut);
				outputStreamAnDieObereSchicht.flush();
				
				//System.out.println("erster Durchlauf..."+this.getName());
				while (status != ConnectionStatus.CLOSED) {
					// TODO
					Object obj = data.poll(100, TimeUnit.MILLISECONDS);
					//System.out.println("polling...");
					//status = ConnectionStatus.CLOSED;
					if(obj==null)
						continue;
					
					
					System.out.println("schiebe Nachricht in den Inputstream: "+obj.toString());
					//status = ConnectionStatus.CLOSED;
					
					outputStreamAnDieObereSchicht.writeObject(obj);
					outputStreamAnDieObereSchicht.flush();
					
					//outputStreamAnDieObereSchicht.flush();
					//inputStreamVonDerOberenSchicht=inputStreamDerOberenSchicht.read();
					//System.out.println("Connection offen");
				}
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}  finally {
				try {
					log.info("CLOSING outputStreamAnDieObereSchicht");
					outputStreamAnDieObereSchicht.close();
					log.info("CLOSING outputStreamAnDieObereSchicht -- DONE");
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

	}

	/**
	 * Thread zum Senden der Daten aus dem inputStreamVonDerOberenSchicht der
	 * darueber liegenden Schicht an den Kommunikationspartner
	 */
	public class DataSenderThread extends Thread {
		public DataSenderThread() {
			setName("DataSenderThread: " + getLocalPort());
		}

		public void run() {
			try {
				// Stream initialisieren
				inputStreamVonDerOberenSchicht = new ObjectInputStream(pipedIn);
				ReliableUdpObject rObj = new ReliableUdpObject();
				//outputStreamDerOberenSchicht.flush();
				status = ConnectionStatus.READY_TO_SEND;
				while (status != ConnectionStatus.CLOSED) {
					//System.out.println("DataSender");
					Object o = inputStreamVonDerOberenSchicht.readObject();
					
			
					//if(o==null)
					//	continue;
					
					// Warte bis wieder gesendet werden darf
					int maxWait=50;
					while(status==ConnectionStatus.SENDING && maxWait>0){
						this.sleep(20);
						maxWait--;
					}
					// TODO
					status = ConnectionStatus.SENDING;
					
					rObj.setData(o);
					rObj.setId(currentOutgoingId++);
					//rObj.setAck(true);
					//System.out.println("LL: "+currentOutgoingId);
					// versenden der Nachricht mit Sendewiederholung
					// TODO
					
					//for(int i=0; i<3 && status==ConnectionStatus.SENDING; i++){
						
					sendIt(remoteAddress, remotePort, rObj);
					//status = ConnectionStatus.CLOSED;
					//warte kurz
					//this.sleep(100);
					//}
					//outputStreamAnDieObereSchicht.writeObject(o);
					
					
				}

			} catch (ClassNotFoundException e1) {
				e1.printStackTrace();
			} catch (IOException e) {
				if (status != ConnectionStatus.CLOSED)
					log.error(e);
			} catch (InterruptedException e) {
				e.printStackTrace();
			} finally {
				try {
					log.info("CLOSING inputStreamVonDerOberenSchicht");
					inputStreamVonDerOberenSchicht.close();
					log.info("CLOSING inputStreamVonDerOberenSchicht -- DONE");
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * Thread, der die empfangenen Nachrichten, die in der ReceivedPackets Queue
	 * abgelegt werden, bearbeitet
	 * 
	 */
	public class ReceivedPacketProcessorThread extends Thread {
		public ReceivedPacketProcessorThread() {
			setName("ReceivedPacketProcessorThread: " + getLocalPort());
			//System.out.println("ReceivedPacketProcessorThread: " + getLocalPort());
			status = ConnectionStatus.READY_TO_SEND;
		}

		@Override
		public void run() {
			try {
				while (status != ConnectionStatus.CLOSED) {
					//System.out.println("ReceivedPacketProcessorThread.......");
					ReliableUdpObject reveivedPdu = receivedPackets.poll(100,
							TimeUnit.MILLISECONDS);
					if (reveivedPdu == null)
						continue;
					// TODO
					//status = ConnectionStatus.READY_TO_SEND;
					lastIncomingId = reveivedPdu.getId();
					System.out.print("empfange ");//+((EchoPDU)reveivedPdu.getData()).getMessage());
					if(reveivedPdu.isAck())
						System.out.println("ack");
					else
						System.out.println(" normale pdu");
					if(!reveivedPdu.isAck()){
						waitTillConnectionIsAccepted(reveivedPdu);
						ReliableUdpObject rObj = new ReliableUdpObject();
						rObj.setAck(true);
						rObj.setId(lastIncomingId);
						rObj.setData(reveivedPdu);
						//currentOutgoingId++;
						
						//ANTWORT zur�ck senden
						
						try{
							sendIt(remoteAddress, remotePort, rObj);
						}
						catch(IOException e){
							e.printStackTrace();
						}
						
						status = ConnectionStatus.READY_TO_SEND;
						
						data.add(reveivedPdu.getData());
						
					}
					else{
						//data.add(reveivedPdu.getData());
						//System.out.println("ack");
						status = ConnectionStatus.READY_TO_SEND;
					}
					
					//EchoPDU rec = (EchoPDU)reveivedPdu.getData();
					
				}
			} catch (InterruptedException e) {
				if (status != ConnectionStatus.CLOSED) {
					log.error("Unvorhergesehene Unterbrechung der Bearbeitung ankommender Pakete",e);
					status = ConnectionStatus.CLOSED;
				}
			} 
		}

		private void waitTillConnectionIsAccepted(
				ReliableUdpObject receivedPacket) {
			/*
			 * Hier wird im Grunde ein Verbindungsaufbau simuliert. Die
			 * Anwendungsschicht ruft ReliableUdpServerSocket.accept auf, um
			 * einen gueltigen ReliableUdpSocket zu erhalten. Bevor
			 * ReliableUdpServerSocket nun den ReliableUdpSocket uebergibt muss
			 * hier wiederum accept aufgerufen werden. Der Vorgang muss
			 * innerhalb von 10 sec abgeschlossen sein (100*10 ms).
			 */
			try {
				int i = 0;
				while (i < 100 && !accepted) {
					Thread.sleep(10);
					i++;
					log.info("SLEEPING waiting for accepted");
				}
				if (!accepted)
					log.error("ERROR ACCEPTING CONNECTION, TOOK TOO LONG ("
							+ receivedPacket.getId() + ")"
							+ socket.getLocalPort() + " -> " + remotePort);

			} catch (InterruptedException e) {
				log.error(e);
			}
		}
	}

	/*
	 * Zeigt an, ob die Verbindung vom Kommunikationspartner akzeptiert wurde.
	 * Spielt nur beim Server eine Rolle, beim Client immer true
	 */
	boolean accepted = false;

	/*
	 * Streams zu Kommunikation mit der ueberliegenden Schicht
	 */
	ObjectOutputStream outputStreamAnDieObereSchicht;
	InputStream inputStreamDerOberenSchicht;
	PipedInputStream pipedIn;
	PipedOutputStream pipedOut;
	OutputStream outputStreamDerOberenSchicht;
	ObjectInputStream inputStreamVonDerOberenSchicht;

	
	/*
	 * Der zugehoerige ServerSocket, der auf einen Port lauscht und alle
	 * zugehoerigen Verbindungen verwaltet
	 */
	ReliableUdpServerSocket verwendeterBasisSocket;

	/*
	 * Moegliche Zustaende des Zustandsautomaten fuer zuverlaessige Verbindungen
	 * auf Basis von ReiableUDPSocket
	 */
	public enum ConnectionStatus {
		CLOSED, READY_TO_SEND, SENDING, WAITING
	};

	private ConnectionStatus status = ConnectionStatus.CLOSED;
	private ConnectionStatus status2 = ConnectionStatus.CLOSED;
	/**
	 * Aktuelle Id der ausgehenden Daten
	 */
	private Long currentOutgoingId = 0L;

	/**
	 * Aktuelle Id der eingehenden Daten
	 */
	private Long lastIncomingId = null;

	/**
	 * Der aktuelle Socket zum Senden von Paketen
	 */
	UnreliableUdpSocket socket;
	/**
	 * FIFO-Queue, die alle der Connection zugeh�rigen empfangenen Pakete
	 * speichert
	 */
	ArrayBlockingQueue<ReliableUdpObject> receivedPackets = new ArrayBlockingQueue<ReliableUdpObject>(
			10);
	/**
	 * FIFO-Queue, die alle f�r die Verbindung empfangenen Datenpakete
	 * speichert, um diese an die darueberliegende Schicht weiterzuleiten
	 */
	ArrayBlockingQueue<Object> data = new ArrayBlockingQueue<Object>(10);

	/**
	 * Der Port des Kommunikationspartners
	 */
	int remotePort;
	/**
	 * Die InetAddress des Kommunikationspartners
	 */
	InetAddress remoteAddress;

	/**
	 * Konstruktor fuer den Aufruf vom Server
	 * 
	 * @param basisSocket
	 * @param remoteAddress
	 * @param remotePort
	 * @throws SocketException
	 */
	protected ReliableUdpSocket(ReliableUdpServerSocket basisSocket,
			InetAddress remoteAddress, int remotePort) throws SocketException {
		this.remoteAddress = remoteAddress;
		this.remotePort = remotePort;
		this.socket = basisSocket.unreliableSocket;
		verwendeterBasisSocket = basisSocket;
		init();
		status = ConnectionStatus.READY_TO_SEND;
	}

	/**
	 * Konstruktor fuer Clients. Die Verbindung zum Server wird aufgebaut und in die Liste
	 * der Verbindungen eingetragen.
	 * 
	 * @param remoteServerAddress
	 * @param ServerPort
	 * @throws SocketException
	 */
	public ReliableUdpSocket(String remoteServerAddress, int serverPort)
			throws SocketException {
		try {
			this.remoteAddress = InetAddress.getByName(remoteServerAddress);
			//System.out.println("remoteAdress gebunden: "+remoteServerAddress);
		} catch (UnknownHostException e) {
			throw new SocketException("Unknown Host__: " + remoteServerAddress);
		}
		this.remotePort = serverPort;

		//TODO
		Integer port = 5100;
		while(ReliableUdpServerSocket.aktivePortsUndDerenListener.containsKey(port++)){
			
		}
		//ReliableUdpServerSocket.aktivePortsUndDerenListener.put(key, value)
		//verwendeterBasisSocket = socket.
		verwendeterBasisSocket = new ReliableUdpServerSocket(port);
	//	System.out.println("verwendeterBasisSocketport: "+verwendeterBasisSocket.port);
		//status = ConnectionStatus.WAITING;
		socket = verwendeterBasisSocket.unreliableSocket;
		init();
		status = ConnectionStatus.READY_TO_SEND;
		//System.out.println("init fertig");
		accept();
	}

	private void init() {

		initializeCommunicationStreams();
		// Sende alle Nachrichten aus der Data Queue an die darueber liegende
		// Schicht ...
		new ForwardDataThread().start();
		// ... und alle Nachrichten aus der darueberliegenden Schicht an
		// die entfernten Kommunikationspartner
		new DataSenderThread().start();
	}

	/**
	 * Initialisiert die Streams, damit Daten an die hoehere Schicht gesendet
	 * werden k�nnen
	 */
	private void initializeCommunicationStreams() {
		ReceivedPacketProcessorThread rt;
		rt = new ReceivedPacketProcessorThread();
		rt.start();
		/**
		 * Durch die PipedStreams koennen Nachrichten, die in die
		 * outputStreamAnDieObereSchicht ueber writeObject() geschrieben werden
		 * in der oberen Schicht ueber den InputStream mit readObject() empfangen
		 * werden. Wichtig: Initialisierungsreihenfolge nicht aendern. Beim
		 * Anlegen der ObjectStreams wird intern eine Initialisierungsnachricht
		 * ausgetauscht. Daher wird hier blockiert.
		 * 
		 * Der Befehl outputStreamAnDieObereSchicht = new
		 * ObjectOutputStream(localout); muss in einem eigenen Thread gestartet
		 * werden (Grund: siehe API-Beschreibung von PipedOutputStream).
		 */
		try {
			pipedOut = new PipedOutputStream();
			inputStreamDerOberenSchicht = new PipedInputStream(pipedOut);

		} catch (IOException e) {
			e.printStackTrace();
		}
		/**
		 * Durch die PipedStreams koennen Nachrichten, die in der oberen Schicht
		 * in den OutputStream geschrieben werden, in dieser Schicht ueber den
		 * inputStreamVonDerOberenSchicht readObject empfangen werden.
		 * 
		 * Wichtig: Initialisierungsreihenfolge nicht aendern. Beim Anlegen der
		 * ObjectStreams wird intern eine Initialisierungsnachricht
		 * ausgetauscht. Daher wird hier blockiert.
		 * 
		 * Der Befehl inputStreamVonDerOberenSchicht = new
		 * ObjectInputStream(pipedIn); muss in einem eigenen Thread gestartet
		 * werden (Grund: siehe API-Beschreibung von PipedInputStream).
		 */
		try {
			pipedIn = new PipedInputStream();
			outputStreamDerOberenSchicht = new PipedOutputStream(pipedIn);
			outputStreamDerOberenSchicht.flush();

		} catch (IOException e) {
			e.printStackTrace();
		}
	}


	/**
	 * Sende Data-PDU an den Kommunikationspartner
	 * 
	 * @param remoteAddress
	 * @param remotePort
	 * @param pdu
	 * @throws IOException
	 */
	public void sendIt(InetAddress remoteAddress, int remotePort, Object pdu)
			throws IOException {
		System.out.print("sende ");
		if(((ReliableUdpObject)pdu).isAck()){
			System.out.println("ack");
		}
		else
			System.out.println(" normale pdu");
		//System.out.println("sende an: "+remoteAddress+" : "+remotePort);
		socket.send(remoteAddress, remotePort, pdu);
	}

	/**
	 * Socket aufraeumen
	 * 
	 * @throws IOException
	 */
	protected void releaseSocket() throws IOException {
		
		log.info("CLOSING SOCKET " + getConnectionString());
		// TODO
		//socket.close();
		status = ConnectionStatus.CLOSED;
		verwendeterBasisSocket.shutdownReliableUdpSocket(this);
	}

	/**
	 * Verbindung schliessen
	 * 
	 * @throws IOException
	 */
	public synchronized void close() throws IOException {
		releaseSocket();
	}

	/**
	 * Inputstream zum Empfang von Daten
	 * 
	 * @return InputStream
	 * @throws IOException
	 */
	public InputStream getInputStream() throws IOException {
		return inputStreamDerOberenSchicht;
	}

	/**
	 * OutputStream zum Empfang von Daten
	 * 
	 * @return outputStream
	 * @throws IOException
	 */
	public OutputStream getOutputStream() throws IOException {
		return outputStreamDerOberenSchicht;
	}

	/**
	 * Der Port des Kommunikationspartners
	 * 
	 * @return remotePort
	 */
	public int getPort() {
		return remotePort;
	}

	/**
	 * Hat derzeit keine Bedeutung
	 * 
	 * @return
	 */
	public synchronized int getReceiveBufferSize() throws SocketException {
		return 0;
	}

	/**
	 * Hat derzeit keine Bedeutung
	 * 
	 * @return
	 */
	public synchronized void setReceiveBufferSize(int size)
			throws SocketException {
	}

	/**
	 * Muss aufgerufen werden, sobald die Connection initalisiert ist und das
	 * Connection Object ReliableUdpSocket der Anwendung �bergeben wird
	 * 
	 * @return
	 */
	protected void accept() {
		accepted = true;
	}

	/**
	 * Wird vom Porthandle aufgerufen. Hier werden saemtliche zu dieser
	 * Connection gehoerenden Pakete (Data + ACK) uebergeben
	 * 
	 * @param receivedPdu
	 * @return
	 */
	protected void process(ReliableUdpObject receivedPdu) {
		receivedPackets.add(receivedPdu);
		//status = ConnectionStatus.WAITING;
		//System.out.println("receivedPdu empfangen");
	}

	/**
	 * Der ConnectionString
	 * 
	 * @return
	 */
	public String getConnectionString() {
		return ReliableUdpServerSocket.getConnectionString(
				socket.getLocalAddress(), socket.getLocalPort(),
				remoteAddress.getHostAddress(), remotePort);
	}

	/**
	 * Der lokale Port
	 * 
	 * @return
	 */
	public int getLocalPort() {
		return socket.getLocalPort();
	}
}