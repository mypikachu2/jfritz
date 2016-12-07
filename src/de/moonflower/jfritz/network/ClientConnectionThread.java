package de.moonflower.jfritz.network;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Random;
import java.util.Timer;
import java.util.Vector;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SealedObject;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;

import org.apache.http.auth.InvalidCredentialsException;
import org.apache.log4j.Logger;

import de.moonflower.jfritz.JFritz;
import de.moonflower.jfritz.callerlist.CallerListListener;
import de.moonflower.jfritz.callmonitor.CallMonitorListener;
import de.moonflower.jfritz.phonebook.PhoneBookListener;
import de.moonflower.jfritz.struct.Call;
import de.moonflower.jfritz.struct.Person;

/**
 * This class is responsible for interacting with a JFritz client.
 * All communications between client and server are asynchronous.
 *
 * Communication between client and server is done using either
 * ClientRequest or String objects. Communication between server
 * and client is done using DataChange or String objects.
 *
 * This thread exits automatically once the connection has been closed.
 *
 * @see de.moonflower.jfritz.network.ClientConnectionListener
 *
 * @author brian
 *
 */
public class ClientConnectionThread extends Thread implements CallerListListener,
			PhoneBookListener, CallMonitorListener {
	private final static Logger log = Logger.getLogger(ClientConnectionThread.class);

	private Socket socket;

	private Login login;

	private InetAddress remoteAddress;

	private ObjectInputStream objectIn;

	private ObjectOutputStream objectOut;

	private Cipher inCipher;

	private Cipher outCipher;

	private ClientConnectionListener connectionListener;

	private DataChange<Call> callsAdd, callsRemove, callUpdate, callMonitor;

	private DataChange<Person> contactsAdd, contactsRemove, contactUpdate;

	private boolean callsAdded=false, callsRemoved=false, callUpdated = false,
		contactsAdded=false, contactsRemoved=false, contactUpdated=false;

	private ServerSenderThread sender;

	private Timer timer;

	private boolean keptAlive;

	public ClientConnectionThread(Socket socket, ClientConnectionListener connectionListener){
		super("Client connection for "+socket.getInetAddress());
		this.socket = socket;
		this.connectionListener = connectionListener;
		remoteAddress = socket.getInetAddress();
	}

	public void run(){

		log.info("NETWORKING: Accepted incoming connection from "+remoteAddress);

		try{
			objectOut = new ObjectOutputStream(socket.getOutputStream());
			objectIn = new ObjectInputStream(socket.getInputStream());

			if((login = authenticateClient()) != null){

				log.info("NETWORKING: Authentication for client "+remoteAddress+" successful!");

					//Reset the timeout
				socket.setSoTimeout(100000);

				//set the timer for the keep alive task
				timer = new Timer();
				ServerKeepAliveTask task = new ServerKeepAliveTask(this, objectOut, remoteAddress, outCipher);
				timer.schedule(task, 5000, 90000);
				keptAlive = true;

				callsAdd = new DataChange<Call>();
				callsAdd.destination = DataChange.Destination.CALLLIST;
				callsAdd.operation = DataChange.Operation.ADD;
				callsRemove = new DataChange<Call>();
				callsRemove.destination = DataChange.Destination.CALLLIST;
				callsRemove.operation = DataChange.Operation.REMOVE;
				callUpdate = new DataChange<Call>();
				callUpdate.destination = DataChange.Destination.CALLLIST;
				callUpdate.operation = DataChange.Operation.UPDATE;
				callMonitor = new DataChange<Call>();
				callMonitor.destination = DataChange.Destination.CALLMONITOR;

				contactsAdd = new DataChange<Person>();
				contactsAdd.destination = DataChange.Destination.PHONEBOOK;
				contactsAdd.operation = DataChange.Operation.ADD;
				contactsRemove = new DataChange<Person>();
				contactsRemove.destination = DataChange.Destination.PHONEBOOK;
				contactsRemove.operation = DataChange.Operation.REMOVE;
				contactUpdate = new DataChange<Person>();
				contactUpdate.destination = DataChange.Destination.PHONEBOOK;
				contactUpdate.operation = DataChange.Operation.UPDATE;

				// create the sender thread, start it up, and set it for the min priority
				sender = new ServerSenderThread(objectOut, remoteAddress, login, outCipher);
				sender.setName("Server sender");
				sender.setDaemon(true);
				sender.start();
				sender.setPriority(Thread.MIN_PRIORITY);

				JFritz.getCallerList().addListener(this);
				JFritz.getPhonebook().addListener(this);
				JFritz.getCallMonitorList().addCallMonitorListener(this);
				waitForClientRequest();

				//clean up resources used and remove any hooks open into
				//the jfritz subsystems
				timer.cancel();
				JFritz.getCallerList().removeListener(this);
				JFritz.getPhonebook().removeListener(this);
				JFritz.getCallMonitorList().removeCallMonitorListener(this);
				objectOut.close();
				objectIn.close();
			}

		}catch(IOException e){
			log.error(e.toString());
			e.printStackTrace();
		} catch (InvalidCredentialsException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (de.robotniko.fboxlib.exceptions.InvalidCredentialsException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		connectionListener.clientConnectionEnded(this);

		log.info("NETWORKING: Client Connection thread for "+remoteAddress+" has ended cleanly");
	}

	/**
	 * this function listens for client requests until the
	 * connection is ended.
	 * @throws de.robotniko.fboxlib.exceptions.InvalidCredentialsException 
	 * @throws InvalidCredentialsException 
	 *
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void waitForClientRequest() throws InvalidCredentialsException, de.robotniko.fboxlib.exceptions.InvalidCredentialsException{
		Object o;
		ClientDataRequest dataRequest;
		ClientActionRequest actionRequest;
		String message;

		while(true){
			try{

				//currently only call list and phone book update
				//requests are supported
				SealedObject sealed_object = (SealedObject)objectIn.readObject();
				o = sealed_object.getObject(inCipher);
				log.info("NETWORKING: received request from "+remoteAddress);
				if(o instanceof ClientDataRequest){

					dataRequest = (ClientDataRequest) o;

					//Process call list request, if the client is allowed
					if(dataRequest.destination == ClientDataRequest.Destination.CALLLIST
							&& login.allowCallList){

						//determine what operation to carry out, if applicable
						if(dataRequest.operation == ClientDataRequest.Operation.GET){

							if(dataRequest.timestamp != null){
								log.info("NETWORKING: Received call list update request from "+remoteAddress);
								log.info("NETWORKING: Timestamp: "+dataRequest.timestamp.toString());
								callsAdded(JFritz.getCallerList().getNewerCalls(dataRequest.timestamp));
							}else{
								log.info("NETWORKING: Received complete call list request from "+remoteAddress);
								callsAdded(JFritz.getCallerList().getUnfilteredCallVector());
							}


						}else if(dataRequest.operation == ClientDataRequest.Operation.ADD && login.allowAddList){

							log.info("NETWORKING: Received request to add "+dataRequest.data.size()+" calls from "+remoteAddress);
							synchronized(JFritz.getCallerList()){
								callsAdded = true;
								JFritz.getCallerList().addEntries(dataRequest.data);
								callsAdded = false;
							}

						}else if(dataRequest.operation == ClientDataRequest.Operation.REMOVE && login.allowRemoveList){

							log.info("NETWORKING: Received request to remove "+dataRequest.data.size()+" calls from "+remoteAddress);
							synchronized(JFritz.getCallerList()){
								callsRemoved = true;
								JFritz.getCallerList().removeEntries(dataRequest.data);
								callsRemoved = false;
							}

						}else if(dataRequest.operation == ClientDataRequest.Operation.UPDATE && login.allowUpdateList){

							log.info("NETWORKING: Received request to update a call from "+remoteAddress);
							synchronized(JFritz.getCallerList()){
								callUpdated = true;
								JFritz.getCallerList().updateEntry((Call) dataRequest.original, (Call) dataRequest.updated);
								callUpdated = false;
							}
						}

						//Process phone book request, if client is allowed
					}else if(dataRequest.destination == ClientDataRequest.Destination.PHONEBOOK
							&& login.allowPhoneBook){

						//determine what operation to carry out, if applicable
						if(dataRequest.operation == ClientDataRequest.Operation.GET){
							log.info("NETWORKING: Received complete phone book request from "+remoteAddress);
							contactsAdded(JFritz.getPhonebook().getUnfilteredPersons());

						}else if(dataRequest.operation == ClientDataRequest.Operation.ADD && login.allowAddBook){

							log.info("NETWORKING: Received request to add "+dataRequest.data.size()+" contacts from "+remoteAddress);
							synchronized(JFritz.getPhonebook()){
								contactsAdded = true;
								JFritz.getPhonebook().addEntries(dataRequest.data);
								contactsAdded = false;
							}

						}else if(dataRequest.operation == ClientDataRequest.Operation.REMOVE && login.allowRemoveBook){

							log.info("NETWORKING: Received request to remove "+dataRequest.data.size()+" contacts from "+remoteAddress);
							synchronized(JFritz.getPhonebook()){
								contactsRemoved = true;
								JFritz.getPhonebook().removeEntries(dataRequest.data);
								contactsRemoved = false;
							}
						}else if(dataRequest.operation == ClientDataRequest.Operation.UPDATE && login.allowUpdateBook){

							log.info("NETWORKING: Received request to update a contact from "+remoteAddress);
							synchronized(JFritz.getPhonebook()){
								contactUpdated = true;
								JFritz.getPhonebook().updateEntry((Person) dataRequest.original, (Person) dataRequest.updated);
								contactUpdated = false;
							}
						}

					}else{
						log.info("NETWORKING: Request from "+remoteAddress+" contained no destination, ignoring");
					}
				}else if(o instanceof ClientActionRequest){
					//client has requested to perform an action
					actionRequest = (ClientActionRequest) o;
					if(actionRequest.action == ClientActionRequest.ActionType.doLookup
							&& login.allowLookup){

						if(actionRequest.number != null && actionRequest.siteName != null){
							log.info("NETWORKING: Received request to do specific reverse lookup for "+actionRequest.number
									+" using "+actionRequest.siteName+ " from "+remoteAddress);
//							JFritzReverseLookup.specificLookup(actionRequest.number, actionRequest.siteName, JFritz.getCallerList());
						}else{
							log.info("NETWORKING: Received request to do complete reverse lookup from "+remoteAddress);
							JFritz.getJframe().doLookupButtonClick();
						}
					}
					else if(actionRequest.action == ClientActionRequest.ActionType.getCallList
							&& login.allowGetList){
						log.info("NETWORKING: Received request to get call from the box from "+remoteAddress);
						JFritz.getJframe().doFetchButtonClick();
					}
					else if(actionRequest.action == ClientActionRequest.ActionType.deleteListFromBox
							&& login.allowDeleteList){
						log.info("NETWORKING: Received request to delete the list from the box from "+remoteAddress);
						JFritz.getJframe().fetchList(null, true);
					}
					else if(actionRequest.action == ClientActionRequest.ActionType.doCall
							&& login.allowDoCall){

							//client has requested a call
						if(actionRequest.number == null){
							log.info("NETWORKING: Received request to list available ports for "+remoteAddress);
							writeAvailablePorts();
						}else if(actionRequest.number != null && actionRequest.port != null){
							log.info("NETWORKING: Received request to dial number "+actionRequest.number.getIntNumber()
									+ " using port "+actionRequest.port+" from "+remoteAddress);

							//TODO: filtering!!
							JFritz.getBoxCommunication().doCall(actionRequest.number, actionRequest.port);

						}else
							log.info("NETWORKING: Received invalid direct dial request from "+remoteAddress);
					}
					else if(actionRequest.action == ClientActionRequest.ActionType.hangup
							&& login.allowDoCall){

							//client has requested a hangup
							JFritz.getBoxCommunication().hangup(actionRequest.port);
					}

				}else if(o instanceof String){
					message = (String) o;

					if(message.equals("JFRITZ CLOSE")){
						log.info("NETWORKING: Client is closing the connection, closing this thread");
						disconnect();
					}else if(message.equals("Party on, Garth!")){
						log.info("NETWORKING: Received keep alive response from client!");
						keptAlive = true;
					}else{
						log.info("NETWORKING: Received message from client: "+remoteAddress+": "+message);
					}

				}else{
					log.info("NETWORKING: Received unexpected object from "+remoteAddress+" ignoring");
				}


			}catch(ClassNotFoundException e){
				log.error("unrecognized class received as request from client: " + e.toString());
				e.printStackTrace();

			}catch(SocketException e){
				if(e.getMessage().toUpperCase().equals("SOCKET CLOSED")){
					log.info("NETWORKING: socket for "+remoteAddress+" was closed!");
				}else{
					log.error(e.toString());
					e.printStackTrace();
				}
				return;
			}catch(EOFException e){
				log.error("client "+remoteAddress+" closed stream unexpectedly: " + e.toString());
				e.printStackTrace();
				return;

			}catch (IOException e){
				log.info("NETWORKING: IOException occured reading client request");
				e.printStackTrace();
				return;

			} catch (IllegalBlockSizeException e) {
				log.error("Illegal block size exception! " + e.toString());
				e.printStackTrace();

			} catch (BadPaddingException e) {
				e.printStackTrace();
//			} catch (WrongPasswordException e) {
//				log.info("NETWORKING: Wrong password exception1");
//				e.printStackTrace();
			}
		}
	}

	/**
	 * Authenticate client and record which login client used
	 * logins are used to determine permissions and eventually
	 * filter settings.
	 *
	 * @return login used by client
	 */
	public Login authenticateClient(){

		Object o;
		//Login login;
		String user;
		Vector<Login> clientLogins = ClientLoginsTableModel.getClientLogins();
		Login login = null;

		try{
			//set timeout in case client implementation is broken
			socket.setSoTimeout(25000);
			// tell the client who we are in plain text
			objectOut.writeObject("JFRITZ SERVER 1.1");
			objectOut.flush();

			// first read in the user name from the client, string is in plain text
			o = objectIn.readObject();
			if(o instanceof String){
				user = (String) o;
				objectOut.flush();

				//find the user in our users table
				for(Login l: clientLogins){
					if(l.getUser().equals(user)){
						login = l;
						break;
					}
				}

				// a correct username was found
				if(login != null){

					// compute the password md5 hash
					MessageDigest md = MessageDigest.getInstance("MD5");
					md.update(login.getPassword().getBytes());

					// create our first private key, the auth key for authentication with the client
					DESKeySpec desKeySpec = new DESKeySpec(md.digest());
					SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("DES");
					SecretKey secretKey = keyFactory.generateSecret(desKeySpec);

					// create the first cipher
					Cipher desCipher = Cipher.getInstance("DES/ECB/PKCS5Padding");
					desCipher.init(Cipher.ENCRYPT_MODE, secretKey);

					// Prepare the data key
					byte[] dataKeySeed = new byte[32];
					Random random = new Random();
					random.nextBytes(dataKeySeed);
					md.reset();
					md.update(dataKeySeed);
					dataKeySeed = md.digest();

					// tell the client the data key using our auth key
					// then close the stream for later usage using the data key
					SealedObject dataKeySeedSealed;
					dataKeySeedSealed = new SealedObject(dataKeySeed, desCipher);
					objectOut.writeObject(dataKeySeedSealed);
					objectOut.flush();

					//create the second private key,  the data key
					desKeySpec = new DESKeySpec(dataKeySeed);
					secretKey = keyFactory.generateSecret(desKeySpec);

					//prepare the two data key ciphers
					inCipher = Cipher.getInstance("DES/ECB/PKCS5Padding");
					outCipher = Cipher.getInstance("DES/ECB/PKCS5Padding");
					inCipher.init(Cipher.DECRYPT_MODE, secretKey);
					outCipher.init(Cipher.ENCRYPT_MODE, secretKey);

					//create the new object streams

					SealedObject sealedObject = (SealedObject)objectIn.readObject();
					o = sealedObject.getObject(inCipher);
					if(o instanceof String){
						String response = (String) o;
						if(response.equals("OK")){
							SealedObject ok_sealed = new SealedObject("OK",outCipher);
							objectOut.writeObject(ok_sealed);
							return login;
						}else{
							log.info("NETWORKING: Client sent false response to challenge!");
						}
					}else{
						log.info("NETWORKING: Client sent false object as response to challenge!");
					}
				}else{
					log.info("NETWORKING: client sent unkown username: "+user);
				}
			}
		}catch (IllegalBlockSizeException e) {
			log.info("NETWORKING: Wrong blocksize for sealed object!");
			log.error(e.toString());
			e.printStackTrace();

		}catch(ClassNotFoundException e){
			log.info("NETWORKING: received unrecognized object from client!");
			log.error(e.toString());
			e.printStackTrace();

		}catch(NoSuchAlgorithmException e){
			log.info("NETWORKING: MD5 Algorithm not present in this JVM!");
			log.error(e.toString());
			e.printStackTrace();

		}catch(InvalidKeySpecException e){
			log.info("NETWORKING: Error generating cipher, problems with key spec?");
			log.error(e.toString());
			e.printStackTrace();

		}catch(InvalidKeyException e){
			log.info("NETWORKING: Error genertating cipher, problems with key?");
			log.error(e.toString());
			e.printStackTrace();

		}catch(NoSuchPaddingException e){
			log.info("NETWORKING: Error generating cipher, problems with padding?");
			log.error(e.toString());
			e.printStackTrace();

		}catch(IOException e){
			log.info("NETWORKING: Error authenticating client!");
			log.error(e.toString());
			e.printStackTrace();
		} catch (BadPaddingException e) {
			log.info("NETWORKING: Bad padding exception!");
			log.error(e.toString());
			e.printStackTrace();
		}

		return null;

	}
	/**
	 * Called internally when client signals that it is going to end
	 * the connection. Is sychronized with all other write requests,
	 * so queued writes should still be written out.
	 *
	 * If you want to close this connection cleanly, then call
	 * closeConnection()
	 *
	 */
	private synchronized void disconnect(){
		try{
			sender.stopThread();
			objectOut.flush();
			objectOut.close();
			objectIn.close();
			socket.close();

		}catch(IOException e){
			log.error(e.toString());
			e.printStackTrace();
		}
	}

	/**
	 * called when the user has chosen to kill all network connections
	 * or when a keep alive timeout has been exceeded
	 *
	 */
	public synchronized void closeConnection(){
		try{
			log.info("Notifying client "+remoteAddress+" to close connection");
			SealedObject sealed_object = new SealedObject("JFRITZ CLOSE", outCipher);
			objectOut.writeObject(sealed_object);
			objectOut.flush();
			sender.stopThread();
			objectOut.close();
			objectIn.close();
			socket.close();

		}catch(SocketException e){
			log.info("NETWORKING: Error closing socket");
			log.error(e.toString());
			e.printStackTrace();

		}catch(IOException e){
			log.error("Error writing close request to client!");
			log.error(e.toString());
			e.printStackTrace();

		}catch(IllegalBlockSizeException e){
			log.error("Error with the block size?");
			log.error(e.toString());
			e.printStackTrace();
		}
	}

	/**
	 * Called when new calls have been added to the call list.
	 * Eventually filters based on login will be applied
	 */
	public synchronized void callsAdded(Vector<Call> newCalls){

		//this thread added calls, no need to write them back
		if(callsAdded || !login.allowCallList){
			callsAdded = false;
			return;
		}

		log.info("NETWORKING: Notifying client "+remoteAddress+" of added calls, size: "+newCalls.size());
		callsAdd.data =  newCalls;

		sender.addChange(callsAdd.clone());

	}

	/**
	 * Called when calls have been removed from the call list.
	 * Eventually filters based on login will be applied.
	 */
	public synchronized void callsRemoved(Vector<Call> removedCalls){

		//this thread removed calls no need to add them back
		if(callsRemoved || !login.allowCallList){
			callsRemoved = false;
			return;
		}

		log.info("NETWORKING: Notifying client "+remoteAddress+" of removed calls, size: "+removedCalls.size());
		callsRemove.data = removedCalls;

		sender.addChange(callsRemove.clone());
	}

	/**
	 * called when a call has been updated (comment changed)
	 */
	public synchronized void callsUpdated(Call original, Call updated){

		if(callUpdated || !login.allowCallList)
			return;

		log.info("NETWORKING: Notifying client "+remoteAddress+" of updated call");
		callUpdate.original = original;
		callUpdate.updated = updated;

		sender.addChange(callUpdate.clone());

	}

	/**
	 * Called when contacts have been added to the call list.
	 * Eventually filters will be applied based on login.
	 *
	 */
	public synchronized void contactsAdded(Vector<Person> newContacts){

		if(contactsAdded || !login.allowPhoneBook)
			return;

		log.info("NETWORKING: Notifying client "+remoteAddress+" of added contacts, size: "+newContacts.size());
		contactsAdd.data = newContacts;

		sender.addChange(contactsAdd.clone());

	}

	/**
	 * Called when contacts have been removed from the call list.
	 * Eventually filters will be applied based on login.
	 *
	 */
	public synchronized void contactsRemoved(Vector<Person> removedContacts){

		if(contactsRemoved || !login.allowPhoneBook)
			return;

		log.info("NETWORKING: Notifying client "+remoteAddress+" of removed contacts, size: "+removedContacts.size());
		contactsRemove.data = removedContacts;

		sender.addChange(contactsRemove.clone());

	}

	/**
	 * called when a contact has been updated by the user
	 * Eventually filters will be applied based on login
	 */
	public synchronized void contactUpdated(Person original, Person updated){

		if(contactUpdated || !login.allowPhoneBook)
			return;

		log.info("NETWORKING: Notifying client "+remoteAddress+" of updated contact");
		contactUpdate.original = original;
		contactUpdate.updated = updated;

		sender.addChange(contactUpdate.clone());
	}

	/**
	 * part of CallMonitorListener
	 */
    public void pendingCallIn(Call call){

    	//make sure the client is allowed to use our call monitor
    	if(!login.allowCallMonitor)
    		return;

    	log.info("NETWORKING: Notifying client "+remoteAddress+" of pending call in");
		callMonitor.original = call;
		callMonitor.operation = DataChange.Operation.ADD;

		sender.addChange(callMonitor.clone());
		callMonitor.original = null;
    }

	/**
	 * part of CallMonitorListener
	 */
    public void establishedCallIn(Call call){

    	//make sure the client is allowed to use our call monitor
    	if(!login.allowCallMonitor)
    		return;

    	log.info("NETWORKING: Notifying client "+remoteAddress+" of established call in");
		callMonitor.original = call;
		callMonitor.operation = DataChange.Operation.UPDATE;

		sender.addChange(callMonitor.clone());
		callMonitor.original = null;
    }

	/**
	 * part of CallMonitorListener
	 */
    public void pendingCallOut(Call call){

    	//make sure the client is allowed to use our call monitor
    	if(!login.allowCallMonitor)
    		return;

    	log.info("NETWORKING: Notifying client "+remoteAddress+" of pending call out");
		callMonitor.updated = call;
		callMonitor.operation = DataChange.Operation.ADD;

		sender.addChange(callMonitor.clone());
		callMonitor.updated = null;
    }

	/**
	 * part of CallMonitorListener
	 */
    public void establishedCallOut(Call call){

    	//make sure the client is allowed to use our call monitor
    	if(!login.allowCallMonitor)
    		return;

    	log.info("NETWORKING: Notifying client "+remoteAddress+" of pending call");
		callMonitor.updated = call;
		callMonitor.operation = DataChange.Operation.UPDATE;

		sender.addChange(callMonitor.clone());
		callMonitor.updated = null;
    }

	/**
	 * part of CallMonitorListener
	 */
    public void endOfCall(Call call){

    	//make sure the client is allowed to use our call monitor
    	if(!login.allowCallMonitor)
    		return;

    	log.info("NETWORKING: Notifying client "+remoteAddress+" of pending call");
		callMonitor.original = call;
		callMonitor.operation = DataChange.Operation.REMOVE;

		sender.addChange(callMonitor.clone());
		callMonitor.original = null;
    }

    /**
     * This function reports the activity state of the connected client
     *
     * @return whether the client has responded to our last keep alive request
     */
    public boolean isClientAlive(){
    	return keptAlive;
    }

    /**
     * This is called to reset the state of the keep alive state once
     * the ServerKeepAliveTask has sent a new keep alive request
     *
     */
    public void resetKeepAlive(){
    	keptAlive = false;
    }

    private void writeAvailablePorts(){
//    	try{
//			Debug.info("Notifying client "+remoteAddress+" of available ports");
//			SealedObject sealed_object = new SealedObject(JFritz.getFritzBox().getAvailablePorts(),
//					outCipher);
//			objectOut.writeObject(sealed_object);
//			objectOut.flush();

//		}catch(IOException e){
//			log.error("Error writing available ports to client!");
//			log.error(e.toString());
//			e.printStackTrace();
//
//		}catch(IllegalBlockSizeException e){
//			log.error("Error with the block size?");
//			log.error(e.toString());
//			e.printStackTrace();
//		}
		log.error("Fix writeAvailablePorts() in ClientConnectionThread!");
    }

    public String toString() {
    	return getClass().getSimpleName();
    }
}
