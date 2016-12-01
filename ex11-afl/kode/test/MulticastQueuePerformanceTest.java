import multicastqueue.*;

import java.io.IOException;
import java.net.InetSocketAddress;

import static java.lang.Integer.parseInt;
import static java.lang.Thread.sleep;

public class MulticastQueuePerformanceTest
{
    /*
     * Develop a benchmark run which compares the running times when you use your
     * implementation with total delivery against the implementation in
     * MulticastQueueFifoOnly.java run with fifo as delivery guarantee.
     *
     * Compare the running times for n=3 peers, n=5 peers and n=7 peers, and
     * n=9, 11, 13, ... and so on as high as you implementation can go,
     * each time with 100 messages.
     *
     * Your report should have a section where you reflect on what you see.
     * How does the running times develop and how do they compare.
     *
     * Does this make sense? Why?
     *
     * If you have the time to experiment with the relation of the running
     * time of these two modes as you let the number of peers grow, you get extra respect.
     */
    public static void main(String args[]) throws IOException {
        /*
        We lets each connected peer (including creator) change turn at putting until
        all 100 messages are sent.
        Then afterwards we let the peers get all 100 messages each, also changing turn.

        We examine the relation between n (constant 100 messages) and x (variable noOfPeers),
        which we measure by getting the total length of time t it takes to put and get
        the messages.
         */

        // n is the number of messages for the test
        // (the total amount of sent messages and how many
        // messages each peer receives)
        int n = 100;
        if (args.length > 0)
        {
            n = parseInt(args[0]);
        }

        // x is the number of peers for the test (increases with 2 after each test)
        int x = 3;
        if (args.length > 1)
        {
            x = parseInt(args[1]);
        }

        // How many times to run each test
        int s = 1;
        if (args.length > 2)
        {
            s = parseInt(args[2]);
        }

        // measuring the time.
        long t,
             testStartTime,
             testStopTime;

        System.out.println("Testing the time of sending " + n +
                " messages and then for each peer receive them all");

        MulticastQueue.DeliveryGuarantee guarantee;
        MulticastQueue[] peers;

        while (true)
        {
            for (int doTwice = 0; doTwice < 2 * s; doTwice++)
            {
                guarantee = (doTwice%2==1)
                            ? MulticastQueue.DeliveryGuarantee.CAUSAL
                            //  ? MulticastQueue.DeliveryGuarantee.TOTAL
                            : MulticastQueue.DeliveryGuarantee.FIFO;

                try {
                    peers = initNetworkOfPeers(x, guarantee);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return;
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }

                // timer start
                testStartTime = System.currentTimeMillis();
                {
                    putMessagesToPeers(n, peers);
                    try {
                        getMessagesFromPeers(n, peers);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        return;
                    }
                }
                // timer stop
                testStopTime = System.currentTimeMillis();

                t = testStopTime - testStartTime;

                // TimerSession ended. Print result

                if (doTwice%2==0)
                {
                    System.out.print(x + " peers:\t\t");
                    System.out.print(guarantee + ": "+t+" ms.");
                    System.out.print("\t\t");

                }
                else
                {
                    System.out.print(guarantee + ": "+t+" ms.");
                    System.out.println();
                }
                try {
                    closeNetworkOfPeers(x, peers);

                    // Allow some time for sockets to become available on the system again.
                    sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            x += 2;
        }
    }

    /**
     * Gets n messages from each peer
     *
     * The peers take turn at getting a message.
     *
     * @param n the amount of message to get from each peer.
     * @param peers the array of peers.
     */
    private static void getMessagesFromPeers(int n, MulticastQueue[] peers)
            throws InterruptedException, IOException
    {
        MulticastQueueGetter getter;
        MulticastMessage rcv;
        for (int i = 0; i < n; i++)
        {
            for (MulticastQueue each : peers)
            {
                //todo: decide if should use getter
                getter = new MulticastQueueGetter(each);

                int waitingTime = 20;
                rcv = getter.getNextMessage(waitingTime * 1000);

                if (rcv == null)
                    throw new IOException(
                            "Peer "+i+" did not receive a payloadMessage within "+waitingTime+" sec.");
                else if (rcv.getClass() != MulticastMessagePayload.class)

                {
                    throw new IOException(
                            "Expected payload ec. @" + i +" : " +  rcv);
                }

                each.get();
            }
        }
    }

    /**
     * Sending n messages.
     *
     * The peers takes turns putting a message until all n have been sent.
     *
     * @param n amount of messages to send
     * @param peers the array of peers
     */
    private static void putMessagesToPeers(int n, MulticastQueue[] peers)
    {
        for (int i = 0; i < n; i++)
        {
            for (int each = 0; each < peers.length; each++)
            {
                peers[each].put(i*100 + each);
            }
        }
    }

    /**
     * Starts the network and get all JoinMessages.
     * @param nOfPeers The size of the network
     * @param guarantee The type of each peer.
     * @return the set of peers.
     * @throws java.io.IOException sometimes
     * @throws InterruptedException sometimes
     */
    private static MulticastQueue[] initNetworkOfPeers( int nOfPeers, MulticastQueue.DeliveryGuarantee guarantee)
            throws InterruptedException, IOException
    {
        MulticastQueue[] res = new MulticastQueue[nOfPeers];

        int newPort = 0;

        MulticastQueue creator;
        creator = PeerFactory.createPeer(guarantee);
        res[0] = creator;

        creator.createGroup(newPort, guarantee);

        MulticastMessage rcv;
        rcv = new MulticastQueueGetter(creator).getNextMessage(2*1000);
        if (rcv == null)
        {
            throw new IOException(
                    "Problem: " + res[0] + " did not get a message");
        }
        else if (rcv.getClass() != MulticastMessageJoin.class)
        {
            throw new IOException(
                    "Problem: " + res[0] +
                    " did not get the joinMessage, but: " + rcv);
        }
        InetSocketAddress creatorAddress = rcv.getSender();

        // build rest of the array from index 1 to nOfPeers
        for (int i = 1; i < nOfPeers; i++)
        {
            res[i] = PeerFactory.createPeer(guarantee);
            res[i].joinGroup(newPort,creatorAddress,guarantee);

        }
        sleep(1000);

        // Get the joinMessages from the join of the group
        for (int i = 1; i < nOfPeers; i++)
        {
            for (int alreadyAdded = i; alreadyAdded >= 0; alreadyAdded--)
            {
                int timeToWait = 4;
                rcv = new MulticastQueueGetter(res[alreadyAdded]).getNextMessage(timeToWait * 1000);
                if (rcv == null)
                {
                    throw new IOException(
                            "Problem: ["+alreadyAdded+"]:" + res[alreadyAdded] +
                            " did not get a message on join within "+timeToWait+" sec.");
                }
                else if (rcv.getClass() != MulticastMessageJoin.class)
                {
                    throw new IOException(
                            "Problem: " + res[alreadyAdded] +
                            " did not get the joinMessage, but: " + rcv);
                }
            }
        }

        try {
            // allow threads to update their registered connections
            //TODO: also total order joinMessages, so this wait is not necessary.
            sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return res;
    }

    private static void closeNetworkOfPeers(int x, MulticastQueue[] peers)
            throws InterruptedException
    {
        try {
            // Allow threads to work their acknowledges through before leaving.
            sleep(100);
            //TODO: also total order leaveMessages, so this wait is not necessary.
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        for (int i = 0; i < x; i++)
        {
            peers[i].leaveGroup();
            for (int rest = i; rest < x; rest++)
            {
                // ignored leaveMessages as not relevant for the test
            }
        }
        peers = null;
    }

    private static class PeerFactory
    {
        public static MulticastQueue createPeer(MulticastQueue.DeliveryGuarantee deliveryGuarantee)
        {
            return new MulticastQueueImpl(deliveryGuarantee);
        }
    }
}
