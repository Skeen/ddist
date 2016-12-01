package multicastqueue.fifo;

import multicastqueue.MulticastMessagePayload;

import java.io.Serializable;
import java.net.InetSocketAddress;

/**
 * Will take objects from pendingSends and send them to all peers.
 * If the queue empties and leaveGroup() was called, then the
 * queue will remain empty, so we can terminate.
 */
class SendingThread<E extends Serializable> extends Thread
{
    private final MulticastQueueFifoOnly<E> q;

    public SendingThread(MulticastQueueFifoOnly<E> q) {
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
            q.sendToAll(new MulticastMessagePayload<E>(q.myAddress, object));
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
