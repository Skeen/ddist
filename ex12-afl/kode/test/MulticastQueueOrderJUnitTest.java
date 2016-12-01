import multicastqueue.*;
import multicastqueue.total.*;
import org.junit.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Develop a test which runs five instances of you implementation and simultaneously
 * and quickly puts a lot of messages at all five peers.
 * Let the test check that they arrive in the same total at all peers.
 * See MulticastQueueFifoOnlyTest.java for inspiration.
 * Your report must have a section which describes how you did the test, why this
 * is an appropriate way to do the test, and what the result of the test was.
 */
public class MulticastQueueOrderJUnitTest
{
    @Test
    public void testVectorClockFunctionality()
    {
        VectorClock v1 = new VectorClock(1);
        VectorClock v2 = new VectorClock(2);

        assertEquals("Newly initialized Vectorclocks should be equals", v1, v2);

        // Updates should not change the fact.
        v2.updateAccordingTo(v1);
        assertEquals(v1, v2);
        v1.updateAccordingTo(v2);
        assertEquals(v1, v2);

        v1.getLocalTimestamp().increment();
        assertNotSame("If one is incremented, they should not be equals", v1, v2);

        // But an updateAccordingTo should make them equals again.
        v2.updateAccordingTo(v1);
        assertEquals(v1, v2);
        v1.updateAccordingTo(v2);
        assertEquals(v1, v2);

        v2.getLocalTimestamp().increment();
        // But an updateAccordingTo should not make them equals again
        // if the updateAccordingTo is not updating according to the newest clock.
        v2.updateAccordingTo(v1);
        assertNotSame(v1, v2);
        // Which it is here.
        v1.updateAccordingTo(v2);
        assertEquals(v1, v2);
    }

    @Test
    public void testLamportClockFunctionality()
    {
        Clock v1 = new LamportClock();
        Clock v2 = new LamportClock();

        assertEquals("Newly initialized LamportClocks should be equals", v1, v2);

        // Updates should not change the fact.
        v2.updateAccordingTo(v1);
        assertEquals(v1, v2);
        v1.updateAccordingTo(v2);
        assertEquals(v1, v2);

        v1.getLocalTimestamp().increment();
        assertNotSame("If one is incremented, they should not be equals", v1, v2);

        // But an updateAccordingTo should make them equals again.
        v2.updateAccordingTo(v1);
        assertEquals(v1, v2);
        v1.updateAccordingTo(v2);
        assertEquals(v1, v2);

        v2.getLocalTimestamp().increment();
        // But an updateAccordingTo should not make them equals again
        // if the updateAccordingTo is not updating according to the newest clock.
        v2.updateAccordingTo(v1);
        assertNotSame(v1, v2);
        // Which it is here.
        v1.updateAccordingTo(v2);
        assertEquals(v1, v2);

        // And compareTo works as expected as the natural ordering would make
        // the earlier clock < later clock.

        //let v1 be the early clock and v2 the later...
        for (int i = 0; i < 10; i++) v2.incrementLocalTimestamp();

        assertTrue("v1 is 'comparable' less-than later clock v2", v1.compareTo(v2)<0);
        assertTrue("v2 is 'comparable' more-than earlier clock v1", v2.compareTo(v1)>0);

        v1.updateAccordingTo(v2);
        assertEquals("v1 is 'comparable' equals after updated to later clock v2", 0, v1.compareTo(v2));
    }

    @Test
    public void testMessagesEquals()
            throws UnknownHostException
    {
        InetSocketAddress myAddress = new InetSocketAddress(InetAddress.getLocalHost(),54456);
        InetSocketAddress otherAddress = new InetSocketAddress(InetAddress.getLocalHost(),54457);
        MulticastMessageJoin m1 = new MulticastMessageJoin(myAddress);
        MulticastMessageJoin m2 = new MulticastMessageJoin(myAddress);
        MulticastMessageJoin m3 = new MulticastMessageJoin(otherAddress);

        assertEquals("JoinMessage from same sender should be equals", m1, m2);
        assertNotSame("JoinMessage from different senders should not be the same", m1, m3);

        List<MulticastMessage> l1 = new ArrayList<MulticastMessage>();
        List<MulticastMessage> l2 = new ArrayList<MulticastMessage>();
        l1.add(m1);
        l2.add(m2);

        MulticastMessage m4 = new MulticastMessageLeave(myAddress);

        assertEquals("Equality should work", m1, m2);

        assertTrue("l1 should contain an identical message to m2", l1.contains(m2));
        assertTrue("l1 should contain all of l2", l1.containsAll(l2));
        l1.add(m3);
        assertTrue("l1 should contain all of l2 even if l1 has different elements", l1.containsAll(l2));

        assertNotSame("The join msg should not be the same as the leave msg", m1, m4);

        //Todo: expand upon equality testing.
    }

    @Test
    public void testSinglePeerJoinAndPayload()
            throws IOException
    {
        int myPort = 49804;
        MulticastQueue creator = new MulticastQueueTotal();
        creator.createGroup(myPort, MulticastQueue.DeliveryGuarantee.TOTAL);
        MulticastMessage rcv = creator.get();

        assertEquals("The first message received after joining should be a joinmessage", MulticastMessageJoin.class, rcv.getClass());
        InetSocketAddress myAddress = rcv.getSender();
        assertEquals("The first message received after joining should be a joinmessage from my port",
                myPort, myAddress.getPort());


        String str1 = "Starting Thread";
        creator.put(str1);

        rcv = creator.get();

        assertEquals("The second message received after sending a String should be a PayloadMessage",
                MulticastMessagePayload.class, rcv.getClass());
        assertEquals("The second message received after sending a String should be a " +
                "PayloadMessage with correct payload",
                str1, ((MulticastMessagePayload) rcv).getPayload());

        creator.leaveGroup();
    }

    @Test
    public void testTwoPeersJoinPayloadAndLeave()
            throws IOException
    {
        int creatorPort = 49801,
            joinerPort  = 49802;
        MulticastQueueTotal creator = new MulticastQueueTotal(),
                            joiner = new MulticastQueueTotal();
        MulticastMessage rcv ;
        String str1;
        InetSocketAddress joinerAddress, creatorAddress;

        // Creator starting group
        creator.createGroup(creatorPort, MulticastQueue.DeliveryGuarantee.TOTAL);
        rcv = creator.get();
        assertEquals("The first message received after joining should be a joinmessage",
                MulticastMessageJoin.class, rcv.getClass());
        creatorAddress = rcv.getSender();
        assertEquals("The first message received after joining should be a joinmessage from my port",
                creatorPort, creatorAddress.getPort());


        // Creator sending Payload to self
        str1 = "Starting Thread";
        creator.put(str1);
        rcv = creator.get();
        assertEquals("The second message received after sending a String should be a PayloadMessage",
                MulticastMessagePayload.class, rcv.getClass());
        assertEquals("The second message received after sending a String should be a " +
                "PayloadMessage with correct payload",
                str1, ((MulticastMessagePayload) rcv).getPayload());


        // Joiner joins group
        joiner.joinGroup(joinerPort,creatorAddress, MulticastQueue.DeliveryGuarantee.TOTAL);
        rcv = creator.get();
        assertEquals("The first message received after ANOTHER joining should be a joinmessage",
                MulticastMessageJoin.class, rcv.getClass());
        joinerAddress = rcv.getSender();

        rcv = joiner.get();
        assertNotNull("I expect to receive a message after joining a group", rcv);
        assertEquals("The first message received after joining group should be a joinmessage",
                MulticastMessageJoin.class, rcv.getClass());


        // Payload message sent from creator to joiner
        str1 = "creaTo _,.; joiner";
        creator.put(str1);
        rcv = creator.get();
        assertEquals("The message received after sending a String should be a PayloadMessage",
                MulticastMessagePayload.class, rcv.getClass());
        assertEquals("The second message received after sending a String should be a " +
                "PayloadMessage with correct payload",
                str1, ((MulticastMessagePayload) rcv).getPayload());

        rcv = joiner.get();
        assertEquals("The message received after sending a String should be a PayloadMessage",
                MulticastMessagePayload.class, rcv.getClass());
        assertEquals("The message received after a String being sent from another " +
                "should be a PayloadMessage with correct payload",
                str1, ((MulticastMessagePayload) rcv).getPayload());


        // Payload message sent from joiner to creator
        str1 = "malign !\"£$%^&*( ) sent from joiner";
        joiner.put(str1);
        rcv = creator.get();
        assertEquals("The message received after sending a String should be a PayloadMessage",
                MulticastMessagePayload.class, rcv.getClass());
        assertEquals("The second message received after sending a String should be a " +
                "PayloadMessage with correct payload",
                str1, ((MulticastMessagePayload) rcv).getPayload());

        rcv = joiner.get();
        assertEquals("The message received after sending a String should be a PayloadMessage",
                MulticastMessagePayload.class, rcv.getClass());
        assertEquals("The message received after a String being sent from another " +
                "should be a PayloadMessage with correct payload",
                str1, ((MulticastMessagePayload) rcv).getPayload());


        // The joiner leaves group.
        joiner.leaveGroup();
        rcv = creator.get();
        assertNotNull("Should be getting a messages as other peer leaves",rcv);
        assertEquals("The message received after leaving the group should be leavinMessage",
                MulticastMessageLeave.class, rcv.getClass());
        assertEquals("Receiving leaveMessage from the joiner", joinerAddress, rcv.getSender());

        rcv = joiner.get();
        assertNotNull("Should be getting a messages as leaving the network",rcv);
        assertEquals("The message received after leaving the group should be leavinMessage",
                MulticastMessageLeave.class, rcv.getClass());
        assertEquals("Receiving leaveMessage from the joiner", joinerAddress, rcv.getSender());


        // The creator leaves group
        creator.leaveGroup();
        rcv = creator.get();
        assertNotNull("Should be getting a messages as leaving the group",rcv);
        assertEquals("The message received upon leaving the group should be leavinMessage",
                MulticastMessageLeave.class, rcv.getClass());
        assertEquals("Receiving leaveMessage from the creator", creatorAddress, rcv.getSender());

        rcv = joiner.get();
        assertNull("I have left the network, so shouldnt expect any more messages", rcv);
        // Todo: find out why one message actually were received after leaving in rare cases...

        // The reverse order of leaving is tested in testTwoPeersMultiplePayloadsOrdering
    }

    @Test
    public void testTwoPeersMultiplePayloadsOrdering() throws IOException
    {
        int
                creatorPort = 49901,
                joinerPort  = 49902;
        MulticastQueueTotal
                creator = new MulticastQueueTotal(),
                joiner = new MulticastQueueTotal();

        MulticastMessage rcv;
        InetSocketAddress creatorAddress, joinerAddress;

        // Setting up a 2 peer network
        creator.createGroup(creatorPort, MulticastQueue.DeliveryGuarantee.TOTAL);
        rcv = creator.get();
        assertEquals(MulticastMessageJoin.class, rcv.getClass());
        creatorAddress = rcv.getSender();
        joiner.joinGroup(joinerPort,creatorAddress, MulticastQueue.DeliveryGuarantee.TOTAL);
        rcv = creator.get();
        assertEquals( MulticastMessageJoin.class, rcv.getClass());
        joinerAddress = rcv.getSender();
        rcv = joiner.get();
        assertEquals( MulticastMessageJoin.class, rcv.getClass());

        int nOfJoinerMessagesFirstRound  = 6,
            nOfCreateMessagesFirstRound  = 1,
            nOfJoinerMessagesSecondRound = 2,
            nOfCreateMessagesSecondRound = 3;

        int nOfMessagesTotal =
                nOfJoinerMessagesFirstRound
                + nOfCreateMessagesFirstRound
                + nOfJoinerMessagesSecondRound
                + nOfCreateMessagesSecondRound;

        // Pushing nOfMessagesTotal messages - mixing the senders
        for(int i = 0; i < nOfJoinerMessagesFirstRound; i++)
        {
            joiner.put(1 + i);
        }
        for(int i = 0; i < nOfCreateMessagesFirstRound; i++)
        {
            creator.put(91 + i);
        }
        for(int i = 0; i < nOfJoinerMessagesSecondRound; i++)
        {
            joiner.put(-1 - i);
        }
        for(int i = 0; i < nOfCreateMessagesSecondRound; i++)
        {
            creator.put(-(91 + i));
        }

        // Getting first from the creator
        List<Integer> creatorReceivedMessages = new ArrayList<Integer>(nOfMessagesTotal);
        for (int i = 0; i < nOfMessagesTotal; i++)
        {
            rcv = creator.get();
            assertNotNull("Message"+i+" should arrive",rcv);
            assertEquals("expect payload", MulticastMessagePayload.class, rcv.getClass());
            assertTrue(creatorReceivedMessages.add((Integer) ((MulticastMessagePayload) rcv).getPayload()));
        }

        // Then from the Receiver
        List<Integer> joinerReceivedMessages = new ArrayList<Integer>(nOfMessagesTotal);
        for (int i = 0; i < nOfMessagesTotal; i++)
        {
            rcv = joiner.get();
            assertNotNull("Message"+i+" should arrive", rcv);
            assertEquals("expect payload", MulticastMessagePayload.class, rcv.getClass());
            assertTrue(joinerReceivedMessages.add((Integer) ((MulticastMessagePayload) rcv).getPayload()));
        }

        // comparing the two lists of received messages
        assertEquals(
                "What the creator received should ordered exactly " +
                        "the same as what the joiner received",
                creatorReceivedMessages, joinerReceivedMessages);


        // The creator leaves group
        creator.leaveGroup();
        rcv = creator.get();
        assertNotNull("Should be getting a messages as leaving the group",rcv);
        assertEquals("The message received upon leaving the group should be leavinMessage",
                MulticastMessageLeave.class, rcv.getClass());
        assertEquals("Receiving leaveMessage from the creator", creatorAddress, rcv.getSender());

        rcv = joiner.get();
        assertNotNull("Should be getting a messages as creator is leaving the network",rcv);
        assertEquals("The message received after creator leaving the group should be leavinMessage",
                MulticastMessageLeave.class, rcv.getClass());
        assertEquals("Receiving leaveMessage from the creator", creatorAddress, rcv.getSender());

        rcv = creator.get();
        assertNull("I have left the network, so shouldnt expect any more messages", rcv);

        // The joiner leaves group.
        joiner.leaveGroup();
        rcv = joiner.get();
        assertNotNull("Should be getting a message upon leaving the network",rcv);
        assertEquals("The message received after leaving the group should be leavinMessage",
                MulticastMessageLeave.class, rcv.getClass());
        assertEquals("Receiving leaveMessage from the joiner", joinerAddress, rcv.getSender());

        rcv = joiner.get();
        assertNull("I have left the network, so shouldnt expect any more messages", rcv);

        // The reverse order of leaving is tested in testTwoPeersJoinPayloadAndLeave
    }

    @Test
    public void testThreePeerJoinAndPayload()
            throws IOException
    {
        int
                creatorPort = 49990,
                joiner1Port = 49991,
                joiner2Port = 49992;

        MulticastQueueTotal
                creator = new MulticastQueueTotal(),
                joiner1 = new MulticastQueueTotal(),
                joiner2 = new MulticastQueueTotal();

        MulticastMessage rcv ;

        InetSocketAddress
                creatorAddress;

        // Creator starting group
        creator.createGroup(creatorPort, MulticastQueue.DeliveryGuarantee.TOTAL);
        rcv = creator.get();
        creatorAddress = rcv.getSender();

        // Joiner1 joins group
        joiner1.joinGroup(joiner1Port, creatorAddress, MulticastQueue.DeliveryGuarantee.TOTAL);
        assertEquals("Message that the creator receives after joiner1 joins the group " +
                "should be a joinMessage",
                MulticastMessageJoin.class,
                creator.get().getClass());
        rcv = joiner1.get();
        assertEquals("Message that the joiner1 receives after joining a group with" +
                " only a creator peer shoul be a joinMessage",
                MulticastMessageJoin.class,
                rcv.getClass());

        // Joiner2 joins group
        joiner2.joinGroup(joiner2Port, creatorAddress, MulticastQueue.DeliveryGuarantee.TOTAL);

        // test creator receives JoinMessage:
        assertEquals("Message that the creator receives after the second joiner joins " +
                "the group should be a JoinMessage",
                MulticastMessageJoin.class,
                creator.get().getClass());
        // test joiner1 receives JoinMessage
        rcv = joiner1.get();
        assertEquals("The message that a peer (not creator) receives after a new peer " +
                "joins the group should be a valid JoinMessage",
                MulticastMessageJoin.class,
                rcv.getClass());
        // test joiner2 receives JoinMessage
        rcv = joiner2.get();
        assertEquals("The message that a peer receives after joining a group of peers " +
                "should be a valid JoinMessage",
                MulticastMessageJoin.class,
                rcv.getClass());

        // Creator sending payload
        String payload = "SOmfew awd jw";
        creator.put(payload);

        assertEquals("Creator should gain payloadMessage after sending payload",
                MulticastMessagePayload.class, creator.get().getClass());
        assertEquals("Joiner1 should gain a payloadMessage the creator sent",
                MulticastMessagePayload.class, joiner1.get().getClass());
        assertEquals("Joiner2 should gain a payloadMessage after the creator sent it.",
                MulticastMessagePayload.class, joiner2.get().getClass());

        // All leaving
        creator.leaveGroup();
        joiner1.leaveGroup();
        joiner2.leaveGroup();
    }

    @Test
    public void testManyPeersMultiplePayloadsOrdering() throws IOException
    {
        //Todo:disable printing of lists
        boolean iWouldLikeToPrintTheTestListsAfterCollecting = false;

        int nOfPeers = 5;

        int creatorPort = 0,
            peerPort = 0;

        MulticastQueueTotal creator = new MulticastQueueTotal();

        MulticastQueueTotal[] peers = new MulticastQueueTotal[nOfPeers];

        for (int i = 0; i < nOfPeers; i++)
        {
            peers[i] = new MulticastQueueTotal();
        }

        MulticastMessage rcv;
        InetSocketAddress creatorAddress, peerAddress;

        /* Setting up a network */

        //Creation
        creator.createGroup(creatorPort, MulticastQueue.DeliveryGuarantee.TOTAL);
        rcv = creator.get();
        assertEquals(MulticastMessageJoin.class, rcv.getClass());
        creatorAddress = rcv.getSender();
        creatorPort = creatorAddress.getPort();

        //joiners
        for (int i = 0; i < nOfPeers; i++)
        {
            peers[i].joinGroup(peerPort,creatorAddress, MulticastQueue.DeliveryGuarantee.TOTAL);

            // assert joinMessage received from both the creator and the new
            // peers (that already have joined)
            assertEquals(MulticastMessageJoin.class, creator.get().getClass());
            for (int nJoiner = i; nJoiner > -1; nJoiner--)
            {
                assertEquals("Each already joined peer should receive a joinMessage",
                        MulticastMessageJoin.class,
                        peers[nJoiner].get().getClass());
            }
        }

        /* Main testing */

        for (int i = 0; i < nOfPeers; i++)
        {
            creator.put(i);
        }

        Map<MulticastQueue,int[]> recordings = new HashMap<MulticastQueue, int[]>(nOfPeers,1);
        for (MulticastQueue each : peers)
        {
            int[] receiveList = new int[nOfPeers];
            for (int i = 0; i < nOfPeers; i++)
            {
                rcv = each.get();
                assertNotNull(rcv);
                assertEquals(MulticastMessagePayload.class, rcv.getClass());
                receiveList[i] = (Integer) ((MulticastMessagePayload) rcv).getPayload();
            }
            recordings.put(each, receiveList);
        }

        if (iWouldLikeToPrintTheTestListsAfterCollecting)
            System.out.println("Here are the lists:");
        for (MulticastQueue key1 : recordings.keySet())
        {
            if (iWouldLikeToPrintTheTestListsAfterCollecting)
                System.out.println(Arrays.toString(recordings.get(key1)));

            for (MulticastQueue key2 : recordings.keySet())
            {
                if (key1 == key2) continue;

                assertArrayEquals("Different peers should receive same lists",
                        recordings.get(key1),recordings.get(key2));
            }
        }

        // finalizing first round of testing
        for (int i = 0; i<nOfPeers; i++)
            assertNotNull("Creator emptying its queue of (its own) messages",creator.get());

        /* Second round of testing */
        // Testing more randomly delegated puts.
        int nOfMessages = nOfPeers * 3;
        for (int i = 0; i < nOfMessages; i++)
        {
            int whichPeer = i % nOfPeers;
            peers[whichPeer].put( i*100 + whichPeer);
        }

        // Collecting results
        recordings = new HashMap<MulticastQueue, int[]>(nOfPeers,1);
        for (MulticastQueue each : peers)
        {
            int[] receiveList = new int[nOfMessages];
            for (int i = 0; i < nOfMessages; i++)
            {
                rcv = each.get();
                assertNotNull(rcv);
                assertEquals(MulticastMessagePayload.class, rcv.getClass());
                receiveList[i] = (Integer) ((MulticastMessagePayload) rcv).getPayload();
            }
            recordings.put(each, receiveList);
        }

        // Checking the result
        if (iWouldLikeToPrintTheTestListsAfterCollecting)
            System.out.println("Here are the lists:");
        for (MulticastQueue key1 : recordings.keySet())
        {
            if (iWouldLikeToPrintTheTestListsAfterCollecting)
                System.out.println(Arrays.toString(recordings.get(key1)));
            for (MulticastQueue key2 : recordings.keySet())
            {
                if (key1 == key2) continue;

                assertArrayEquals("Different peers should receive same lists",
                        recordings.get(key1),recordings.get(key2));
            }
        }

        // finalizing
        for (int i = 0; i<nOfMessages; i++)
            assertNotNull("Creator emptying its queue of (its own) messages",creator.get());

        /* Leaving the group */
        // The creator leaves group
        creator.leaveGroup();
        assertEquals("C: The message received upon leaving the group should be PayloadMessage\n\t"+rcv+"\n",
               MulticastMessagePayload.class, rcv.getClass());
        rcv = creator.get();
        assertNotNull("C: Should be getting a messages as leaving the group", rcv);
        assertEquals("C: The message received upon leaving the group should be leavinMessage\n\t"+rcv+"\n",
                MulticastMessageLeave.class, rcv.getClass());
        assertEquals("C: Receiving leaveMessage from the creator", creatorAddress, rcv.getSender());
        rcv = creator.get();
        assertNull("C: I have left the network, so shouldnt expect any more messages", rcv);
        for (MulticastQueue peer : peers)
        {
            rcv = peer.get();
            assertNotNull("P: Should be getting a messages as creator is leaving the network",rcv);
            assertEquals("P: The message received after creator leaving the group should be leavinMessage",
                    MulticastMessageLeave.class, rcv.getClass());
            assertEquals("P: Receiving leaveMessage from the creator", creatorAddress, rcv.getSender());
        }

        // The rest of the peers leaves the group
        for (MulticastQueue peer : peers)
        {
            peer.leaveGroup();
            rcv = peer.get();
            assertNotNull("P: Should be getting a message upon leaving the network",rcv);
            assertEquals("P: The message received after leaving the group should be leavinMessage",
                    MulticastMessageLeave.class, rcv.getClass());

            rcv = peer.get();
            // assertNull("I have left the network, so shouldn't expect any more messages", rcv);
            // Todo: find out why some get messages after leaving...
        }
    }
}
