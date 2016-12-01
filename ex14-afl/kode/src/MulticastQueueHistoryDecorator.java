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

import multicastqueue.*;

import java.io.IOException;
import java.io.Serializable;
import java.net.*;
import java.util.*;

public class MulticastQueueHistoryDecorator<E extends Serializable> implements MulticastQueue<E>
{
    private MulticastQueueFifoOnly<E> queue;

    public MulticastQueueHistoryDecorator()
    {
        queue = new MulticastQueueFifoOnly<E>();
    }
	
	public int createGroup(int port, DeliveryGuarantee deliveryGuarantee) 
	    throws IOException
    {
        return queue.createGroup(port, deliveryGuarantee);
    }
	
	public int joinGroup(int port, InetSocketAddress knownPeer, DeliveryGuarantee deliveryGuarantee) throws IOException
    {
        return queue.joinGroup(port, knownPeer, deliveryGuarantee);
    }

    public MulticastMessage get()
    {
		MulticastMessage msg;
		
		// Manly Tears.
		for(;;)
        {
			msg = queue.get();
			if(msg instanceof MulticastMessagePayload)
			{
				String payload = (String) ((MulticastMessagePayload) msg).getPayload();
				if(payload.startsWith("OLD_CHAT_NEEDED"))
				{
					// Message contains information required for response, Tokenizer splits the string into relevant parts.
					StringTokenizer st = new StringTokenizer(payload, "===");
                    
					if(st.countTokens() != 3)
					{
						// malformed old chat request, assuming it as normal text message.
						return msg;
					}

					String start = st.nextToken();
					String host = st.nextToken();
					String client = st.nextToken();
					
					String client_hostname = getHost(client);
					int client_port = getPort(client);
					
					String myHost = MultiChat.getHost() + ":" + MultiChat.getExtraPort();
                    
					if(!myHost.equals(client))
					{
					
						PointToPointQueueSenderEnd<MulticastMessagePayload<ArrayList<MulticastMessage>>> sender = new PointToPointQueueSenderEndNonRobust<MulticastMessagePayload<ArrayList<MulticastMessage>>>();
						
						sender.setReceiver(new InetSocketAddress(client_hostname, client_port));
						
						ArrayList<MulticastMessage> history = MultiChat.getHistory();
						sender.put(
                                new MulticastMessagePayload<ArrayList<MulticastMessage>>(
                                        new InetSocketAddress(MultiChat.getHost(), MultiChat.getPort() ),
                                        history));
						
						while(!sender.isEmpty()) {}
							
						sender.shutdown();
                    }
                    // Do not return special message, try again.
					continue;
				}
			}
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