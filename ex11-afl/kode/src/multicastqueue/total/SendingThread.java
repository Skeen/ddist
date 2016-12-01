package multicastqueue.total;

import multicastqueue.MulticastMessage;
import multicastqueue.MulticastMessagePayload;
import multicastqueue.MulticastQueue;

import java.io.Serializable;
import java.net.InetSocketAddress;

/**
* Will take objects from pendingSends and send them to all peers.
* If the queue empties and leaveGroup() was called, then the
* queue will remain empty, so we can terminate.
*/
class SendingThread<E extends Serializable> extends Thread
{
    private MulticastQueueTotal<E> q;

    // Precondition: q has been initialized (with q.init())
    // - notably q.myAddress has been initialized.
    public SendingThread(MulticastQueueTotal<E> q) {
        super("Q-Sending @"+q.myAddress); // name the thread
        this.q = q;
    }

    public void run()
    {
        q.log("starting sending thread.");
        // As long as we are not leaving or there are objects to
        // send, we will send them.
        waitForPendingSendsOrLeaving();
        E object = q.pendingSends.poll();
        while (object != null)
        {
            MulticastMessage msg = new MulticastMessagePayload<E>(q.myAddress, object);

            // Wrap with a VectorClock.
            if (q.deliveryGuarantee == MulticastQueue.DeliveryGuarantee.TOTAL)
            {
                //test
                String test1 = "I updated my clock from " + q.clock + " to ";

                q.clock.incrementLocalTimestamp();

                //test
                q.log(test1 + q.clock + " and attached the clock to " + msg);

                msg = new TimestampedMessage( msg, q.clock.clone());
            }

            q.sendToAll(msg);
            waitForPendingSendsOrLeaving();
            object = q.pendingSends.poll();
        }
        q.log("shutting down outgoing connections.");
        synchronized (q.outgoing)
        {
            for (InetSocketAddress address : q.outgoing.keySet())
            {
                q.disconnectFrom(address);
            }
        }
        q.log("stopping sending thread.");
    }

    /**
     * Used by the sending thread to wait for objects to enter the
     * collection or us having left the group. When the method
     * returns, then either the collection is non-empty, or the
     * multicast queue was called in leaveGroup();
     */
    private void waitForPendingSendsOrLeaving()
    {
        synchronized (q.pendingSends)
        {
            while (q.pendingSends.isEmpty() && !q.isLeaving)
            {
                try
                {
                    // We will be woken up if an object arrives or we
                    // are leaving the group. Both might be the case
                    // at the same time.
                    q.pendingSends.wait();
                }
                catch (InterruptedException e)
                {
                    // Probably leaving. The while condition will
                    // ensure proper behavior in case of some other
                    // interruption.
                }
            }
            // Now: pendingSends is non empty or we are leaving the group.
        }
    }
}
