/**
 * Kode skrevet af;
 *
 * Hold 4; Gruppe: 2
 * Emil Madsen - 20105376
 * Rasmus - 20105109
 * Sverre - 20083549
 *
 * {skeen, emray, sverre}@cs.au.dk
 */

package multicastqueue;

import java.io.IOException;
import java.io.Serializable;
import java.net.*;
import java.util.*;
import pointtopointqueue.*;
import replicated_calculator.*;
import java.util.UUID;

public class MulticastQueueHistoryDecorator<E extends Serializable> implements MulticastQueue<E>
{
    private MulticastQueue queue;
    private int myPort = 0;
    private int historyPort = 0;
    private String myIP;
    private boolean recievable;
    private ArrayList<MulticastMessage> history = new ArrayList<MulticastMessage>();
    
    public MulticastQueueHistoryDecorator(MulticastQueue queue)
    {
        recievable = true;
        this.queue = queue;
        try
        {
            myIP = InetAddress.getLocalHost().getHostAddress();
        } 
        catch (Exception e) 
        {
            System.out.println("Unknown host IP error.");
        }
    }
    
    public void log(MulticastMessage msg)
    {
        history.add(msg);
    }
    
    public ArrayList<MulticastMessage> getHistory()
    {
        return history;
    }
    
    public void recieveHistory(InetSocketAddress knownPeer) throws IOException
    {
        if(!recievable)
        {
            throw new UnsupportedOperationException("RecieveHistory after get");
        }

        PointToPointQueueReceiverEnd<MulticastMessagePayload<ArrayList<MulticastMessage>>> reciever = new PointToPointQueueReceiverEndNonRobust<MulticastMessagePayload<ArrayList<MulticastMessage>>>();
        historyPort = reciever.listenOnPort(0);
        
        String s = "OLD_CHAT_NEEDED===" + knownPeer.getAddress().getHostAddress() + ":" + knownPeer.getPort() + "===" + myIP + ":" + historyPort;
        queue.put(new ClientEventMessage(myIP, new Random().nextInt(), s));
		
		history = reciever.get().getPayload();
        
        //TODO: Do something with new history.
        
        reciever.shutdown();
    }
	
	public int createGroup(int port, DeliveryGuarantee deliveryGuarantee) throws IOException
    {
        myPort = queue.createGroup(port, deliveryGuarantee);
        return myPort;
    }
	
	public int joinGroup(int port, InetSocketAddress knownPeer, DeliveryGuarantee deliveryGuarantee) throws IOException
    {
        myPort = queue.joinGroup(port, knownPeer, deliveryGuarantee);
        recieveHistory(knownPeer);
        return myPort;
    }

    public MulticastMessage get()
    {
        recievable = false;
		MulticastMessage msg;
		
		//Manly Tears.
		for(;;)
        {
			msg = queue.get();

            System.out.println(msg);

			if(msg instanceof MulticastMessagePayload)
			{
                MulticastMessagePayload<ClientEvent> payloadMsg = (MulticastMessagePayload<ClientEvent>) msg;
                ClientEvent event = payloadMsg.getPayload();
                ClientEventMessage eventmsg = null;
                if(event instanceof ClientEventMessage)
                {
                    eventmsg = (ClientEventMessage) event;
                    String payload = eventmsg.getString();
                    if(payload.startsWith("OLD_CHAT_NEEDED"))
                    {
                        // Message contains information required for response, Tokenizer splits the string into relevant parts.
                        StringTokenizer st = new StringTokenizer(payload, "===");

                        if(st.countTokens() != 3)
                        {
                            // malformed old chat request, assuming it as normal text message.
                            log(msg);
                            return msg;
                        }

                        String start = st.nextToken();
                        String host = st.nextToken();
                        String client = st.nextToken();

                        String client_hostname = getHost(client);
                        int client_port = getPort(client);

                        String myHost = myIP + ":" + historyPort;

                        if(!myHost.equals(client))
                        {
                            PointToPointQueueSenderEnd<MulticastMessagePayload<ArrayList<MulticastMessage>>> sender = new PointToPointQueueSenderEndNonRobust<MulticastMessagePayload<ArrayList<MulticastMessage>>>();

                            sender.setReceiver(new InetSocketAddress(client_hostname, client_port));

                            ArrayList<MulticastMessage> history = getHistory();
                            sender.put( new MulticastMessagePayload<ArrayList<MulticastMessage>>(new InetSocketAddress(myIP, myPort), history));

                            while(!sender.isEmpty()) {}

                            sender.shutdown();
                        }
                        // Do not return special message, try again.
                        continue;
                    }
                }
			}
            log(msg);
			return msg;
		}
    }

	public void put(E object)
    {
        queue.put(object);
    }

	public void leaveGroup()
    {
        queue.leaveGroup();
    }
		
	public void run()
    {
        //Can't actually run this, as the contained queue starts itself and this class is not threaded.
    }
	
	private int getPort(String connectString)
    {
        int connectHostport;

        int colon = connectString.indexOf(":");
        if(colon == -1)
        {
            return -1;
        }
        else
        {
            // Break up the message, into the hostString and the portString
            String hostString = connectString.substring(0, colon);
            String portString = connectString.substring(colon+1);

            // Save / parse the two
            connectHostport = Integer.parseInt(portString);

            return connectHostport;
        }
    }
    
    private String getHost(String connectString)
    {
        String connectHostname;
        int connectHostport = -1;

        int colon = connectString.indexOf(":");
        if(colon == -1)
        {
            return "";
        }
        else
        {
            // Break up the message, into the hostString and the portString
            String hostString = connectString.substring(0, colon);
            String portString = connectString.substring(colon+1);

            // Save / parse the two
            connectHostname = hostString;
            connectHostport = Integer.parseInt(portString);

            return connectHostname;
        }
    }
}
