import multicastqueue.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

public class MulticastQueuePerformanceTest
{
    /*
     * Develop a benchmark run which compares the running times when you use your implementation with total delivery against the implementation in MulticastQueueFifoOnly.java run with fifo as delivery guarantee.
     * Compare the running times for n=3 peers, n=5 peers and n=7 peers, and n=9, 11, 13, ... and so on as high as you implementation can go, each time with 100 messages.
     * Your report should have a section where you reflect on what you see.
     * How does the running times develop and how do they compare.
     *
     * Does this make sense? Why?
     * If you have the time to experiment with the relation of the running time of these two modes as you let the number of peers grow, you get extra respect.
     */
    public static void main(String args[])
            throws IOException, InterruptedException {
        //TODO: Make ant work with the parsing
        // #Entities
        int entities = 15; //Integer.parseInt(args[0]);
        // #Messages/entity
        int testSize = 100; //Integer.parseInt(args[1]);
        // TestClass
        String deliveryGuarantee = "fifo"; //args[2];
        MulticastQueue.DeliveryGuarantee guarantee = null;
        if(deliveryGuarantee.equals("fifo"))
        {
            guarantee = MulticastQueue.DeliveryGuarantee.FIFO;
        }
        else if(deliveryGuarantee.equals("total"))
        {
            guarantee = MulticastQueue.DeliveryGuarantee.TOTAL;
        }
        else
        {
            System.exit(-1);
        }

        // Lets just run everything on the localhost,
        // so lets find the address of ourselves:
        InetAddress localhost = InetAddress.getLocalHost();

        // Make a list of peers, and create them
        List<MulticastQueue<Integer>> peers = new ArrayList<MulticastQueue<Integer>>();
        for(int x=0; x<entities; x++)
        {
            // NOTE: The MulticastQueueImpl changes internally depended on the
            // requested delivery guarantee.
            MulticastQueue<Integer> peer = new MulticastQueueImpl<Integer>();
            peers.add(peer);
        }

        // Start connecting the everything.
        System.out.println("FORMING GROUP");
        int port = peers.get(0).createGroup(0, guarantee);
        MulticastQueueGetter.wait(1);

        System.out.println("DEBUG: Group started at: " + port);

        //START TIMING?
        long time = System.currentTimeMillis();

        for(MulticastQueue<Integer> peer : new RestIterator<MulticastQueue<Integer>>(peers))
        {
            int peer_port = peer.joinGroup(0, new InetSocketAddress(localhost,port), guarantee);
            //MulticastQueueGetter.wait(1);

            System.out.println("DEBUG: Peer at: " + peer_port + " joined the group");
        }

        // Start sending out stuff
        System.out.println("BROADCASTING");
        for (int i=0; i<testSize*entities; i++) 
        {
            int id = (i % entities);
            peers.get(id).put(i);
        }
        //MulticastQueueGetter.wait(10);

        // Start leaving groups
        System.out.println("LEAVING GROUP");
        for(MulticastQueue<Integer> peer : peers)
        {
            peer.leaveGroup();
            //MulticastQueueGetter.wait(1);
        }

        // Check if there's any messages not following total
        // (Simply check if all messages are in peer0's total).
        System.out.println("GENERATING MESSAGE PATTERN");
        List<MulticastMessage> msgList = null;
        MulticastQueue<Integer> super_peer = peers.get(0);
        MulticastQueueGetter<Integer> super_getter = new MulticastQueueGetter<Integer>(super_peer);
        msgList = super_getter.getAllMessages(5);

        System.out.println("CHECKING MESSAGE PATTERN");
        for(MulticastQueue<Integer> peer : new RestIterator<MulticastQueue<Integer>>(peers))
        {
            MulticastQueueGetter<Integer> getter = new MulticastQueueGetter<Integer>(peer);
            List<MulticastMessage> messageList = getter.getAllMessages(5);
            /*
            System.out.print("Checking..."); 
            if(msgList.equals(messageList))
            {
                System.out.println("EQUAL");
            }
            else
            {
                System.out.println("NOT EQUAL");
                printFlaw(getPayLoadMessages(msgList), getPayLoadMessages(messageList));
            }
            */
        }

        System.out.println("TEST DONE");
        time = System.currentTimeMillis() - time;
        System.out.println(" The test took " + time + " milliseconds");
    }

    public static List<MulticastMessage> getPayLoadMessages(List<MulticastMessage> msgList)
    {
        List<MulticastMessage> payload = new ArrayList<MulticastMessage>();
        for(MulticastMessage msg : msgList)
        {
            if(msg instanceof MulticastMessagePayload)
            {
                payload.add(msg);
            }
        }
        return payload;
    }

    public static void printList(List<MulticastMessage> msgList)
    {
        for(MulticastMessage msg : msgList)
        {
            System.out.println("       " + msg);
        }
    }

    public static void printFlaw(List<MulticastMessage> msgList1, List<MulticastMessage> msgList2)
    {
        if(msgList1.size() != msgList2.size())
        {
            System.out.println("DIFFERENT SIZES!");
            return;
        }

        for(int x=0; x<msgList1.size(); x++)
        {
            MulticastMessage msg1 = msgList1.get(x);
            MulticastMessage msg2 = msgList2.get(x);

            if(! msg1.equals(msg2))
            {
                System.out.println("DIFFERENCE IN:");
                System.out.println("       " + msg1);
                System.out.println("       " + msg2);
                return;
            }
        }
    }
}
