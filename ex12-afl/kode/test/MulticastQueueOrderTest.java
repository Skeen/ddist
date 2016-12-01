import multicastqueue.MulticastMessage;
import multicastqueue.MulticastMessagePayload;
import multicastqueue.MulticastQueue;
import multicastqueue.MulticastQueueImpl;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Develop a test which runs five instances of you implementation and simultaneously
 * and quickly puts a lot of messages at all five peers.
 * Let the test check that they arrive in the same total at all peers.
 * See MulticastQueueFifoOnlyTest.java for inspiration.
 * Your report must have a section which describes how you did the test, why this
 * is an appropriate way to do the test, and what the result of the test was.
 */
public class MulticastQueueOrderTest
{
    private static List<MulticastMessagePayload> getPayLoadMessages(List<MulticastMessage> msgList)
    {
        List<MulticastMessagePayload> payload = new ArrayList<MulticastMessagePayload>();
        for(MulticastMessage msg : msgList)
        {
            if(msg instanceof MulticastMessagePayload)
            {
                payload.add((MulticastMessagePayload) msg);
            }
        }
        return payload;
    }

    private static void printFlaw(List<MulticastMessagePayload> msgList1, List<MulticastMessagePayload> msgList2)
    {
        String print = "I want a printout of the first list\n\t";
        System.out.println(print + msgList1);

        print = "I want a printout of the second list\n\t";
        System.out.println(print + msgList2);

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

    public static void main(String[] args)
            throws IOException, InterruptedException {
        System.out.println("welcome to manual test");
        // #Entities
        int entities = 5; //Integer.parseInt(args[0]);
        // #Messages/entity
        int testSize = 19; //Integer.parseInt(args[1]);
        // TestClass
        String deliveryGuarantee = args[0];

        //String deliveryGuarantee = "fifo"; //args[2];
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
        System.out.println("Testing multicastqueue with deliveryGuarantee " + guarantee);

        // Lets just run everything on the localhost,
        // so lets find the address of ourselves:
        InetAddress localhost = InetAddress.getLocalHost();

        // Make a list of peers, and create them
        List<MulticastQueue<Integer>> peers = new ArrayList<MulticastQueue<Integer>>();
        for(int x=0; x<entities; x++)
        {
            // NOTE: The MulticastQueueImpl changes internally depended on the
            // requested delivery guarantee.
            MulticastQueue<Integer> peer = new MulticastQueueImpl<Integer>(guarantee);
            peers.add(peer);
        }

        // Start connecting the everything.
        System.out.println("FORMING GROUP");
        int port = peers.get(0).createGroup(0, guarantee);

        System.out.println("DEBUG: Group started at: " + port);

        for(MulticastQueue<Integer> peer : peers)
        {
            if (peer == peers.get(0)) continue;

            int peer_port = peer.joinGroup(0, new InetSocketAddress(localhost,port), guarantee);

            System.out.println("DEBUG: Peer at: " + peer_port + " joined the group");
        }

        // Start sending out stuff
        System.out.println("BROADCASTING");
        for (int i=0; i<testSize; i++)
        {
            int id = (i % entities);
            peers.get(id).put(i);
        }

        // Start leaving groups
        System.out.println("LEAVING GROUP");
        for(MulticastQueue<Integer> peer : peers)
        {
            peer.leaveGroup();
        }

        // Check if there's any messages not following total
        // (Simply check if all messages are in peer0's total).
        System.out.println("GENERATING MESSAGE PATTERN");
        List<MulticastMessage> msgList;
        MulticastQueue<Integer> super_peer = peers.get(0);
        MulticastQueueGetter<Integer> super_getter = new MulticastQueueGetter<Integer>(super_peer);
        msgList = super_getter.getAllMessages(10);

        System.out.println("CHECKING MESSAGE PATTERN");
        for (MulticastQueue<Integer> peer : peers)
        {
            if (peer == super_peer) continue;
            System.out.println("This is " + peer);

            MulticastQueueGetter<Integer> getter = new MulticastQueueGetter<Integer>(peer);
            List<MulticastMessage> messageList = getter.getAllMessages(10);
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
        }
    }
}
