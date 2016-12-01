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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;

public class MultiChat
{
    // The port number that we're defaulting to (0=free one)
    public static final int DEFAULT_PORT_NUMBER = 0;
	private static int hostPort = 0;
	private static int extraPort = 0;
	private static ArrayList<MulticastMessage> history = new ArrayList<MulticastMessage>();

    private MulticastQueue<String> queue;

    public MultiChat()
    {
        // Initialise our message queue
        queue = new MulticastQueueHistoryDecorator<String>();
    }
	
	public static void log(MulticastMessage msg)
	{
		history.add(msg);
	}
	
	public static ArrayList<MulticastMessage> getHistory()
	{
		return history;
	}

    private void run(int port) throws IOException
    {
        // Create a new group, and be the sole member
        hostPort = queue.createGroup(port, MulticastQueue.DeliveryGuarantee.FIFO);
        System.out.println("Created group!" + "(PORT:" + port + ")");
        
		runChat();
    }

    private void run(String hostname, int hostport, int ownPort) throws IOException
    {
        // Connect to the MultiChat server on another machine, using the
        // the provided arguments
        
        InetSocketAddress knownPeer = new InetSocketAddress(hostname, hostport);

        hostPort = queue.joinGroup(ownPort, knownPeer, MulticastQueue.DeliveryGuarantee.FIFO);
        
        System.out.println( "Joined group!" + "(PORT:" + hostport + ")");
        
        recieveOldChat(hostname, hostport);
        
		runChat();
    }
	
	private void runChat()
	{   
        // Output thread
        OutputThread out = new OutputThread(queue);
        out.start();
        
        // Input thread
        InputThread in = new InputThread(queue);
        in.start();
        
		try
		{
			in.join();
		} 
		catch (Exception ignored) {}
		
	}

	private void recieveOldChat(String hostname, int hostport) throws IOException
    {
        PointToPointQueueReceiverEnd<MulticastMessagePayload<ArrayList<MulticastMessage>>> reciever = new PointToPointQueueReceiverEndNonRobust<MulticastMessagePayload<ArrayList<MulticastMessage>>>();
        extraPort = reciever.listenOnPort(0);

        String s = "OLD_CHAT_NEEDED===" + hostname + ":" + hostport + "===" + getHost() + ":" + extraPort;
        queue.put(s);
		
		ArrayList<MulticastMessage> history = reciever.get().getPayload();
		
        history.remove(history.size()-1);
		for(MulticastMessage m : history)
		{
			OutputThread.handleCast(m);
		}
        reciever.shutdown();
    }
	
    public static String getHost()
    {
		try
		{
			InetAddress localhost = InetAddress.getLocalHost();
			return localhost.getHostAddress();
		}
		catch(Exception e)
		{
			e.printStackTrace();
			return null;
        }
    }

    public static int getPort()
    {
        return hostPort;
    }
	
	public static int getExtraPort()
	{
		return extraPort;
	}

    public static void main(String[] args)
    {
        try
        {
            // Prepare the multiChat queue
            MultiChat m = new MultiChat();
			
            // If there's not given any arguments,
            // do simply host on the default port.
            if(args.length < 1)
            {
                m.run(DEFAULT_PORT_NUMBER);
            }
            else
            {
                // There's given at least one argument,
                // we'll use the first one as hostname
                String hostname;
                int hostport = DEFAULT_PORT_NUMBER;

                String argument = args[0];

                // We'll figure out where the colon is, if any
                // (in case we're going to connect to a non default port)
                int colon = argument.indexOf(":");
                if(colon == -1)
                {
                    // No colon found, simply take the the first argument as
                    // hostname, and use the default port.
                    hostname = argument;
                }
                else
                {
                    // Break up the message, into the hostString and the portString
                    String hostString = argument.substring(0, colon);
                    String portString = argument.substring(colon+1);

                    // Save / parse the two
                    hostname = hostString;
                    hostport = Integer.parseInt(portString);

                    System.out.println("hostname=" + hostname
                         + "    hostport=" + hostport);
                }

                // And run the connect code
                m.run(hostname, hostport, DEFAULT_PORT_NUMBER);
            }
        }
        catch (Exception e)
        {
            //IO exception on running.
            e.printStackTrace();
            System.exit(-1);
        }
    }
}