package multicastqueue.total;

import multicastqueue.MulticastMessage;
import multicastqueue.MulticastQueue;

import java.io.Serializable;

/**
 * This is the receiving thread. This is just a dispatcher: it
 * will receive the incoming message and call the respective
 * handlers.
 */
class ReceivingThread<E extends Serializable>
        extends Thread
{
    private final MulticastQueueTotal<E> q;

    // Precondition: q has been initialized (with q.init())
    // - notably q.myAddress has been initialized.
    public ReceivingThread(MulticastQueueTotal<E> q) {
        super("Q-Receiving @" + q.myAddress); // name the thread
        this.q = q;
    }

    public void run()
    {
        q.log("starting receiving thread.");
        MulticastMessage msg = q.incoming.get();
        /* By contract we know that msg == null only occurs if
         * incoming is shut down, which we are the only ones that can
         * do, so we use that as a way to kill the receiving thread
         * when that is needed. We shut down the incoming queue when
         * it happens that we are leaving down and all peers notified
         * us that they shut down their connection to us, at which
         * point no more message will be added to the incoming
         * queue. */
        while (msg != null)
        {
            /* Already printing in the message handlers
            String s = q.myAddress.toString(); s = s.substring(s.lastIndexOf('.'));
            q.log("I received a message:\n\t" + msg);
            */

            // Dispatch the handling of the messages.
            q.dispatchHandling(msg);

            if (q.deliveryGuarantee == MulticastQueue.DeliveryGuarantee.TOTAL)
            {
                //test
                String test1 = "I updated my clock from " + q.clock + " to ";

                q.clock.incrementLocalTimestamp();

                //test
                q.log(test1 + q.clock + " after handling a message");
            }

            msg = q.incoming.get();
        }

        /* Before we terminate we notify callers who are blocked in
         * out get() method that no more gets will be added to the
         * buffer pendingGets. This allows them to return with a null
         * in case no message are in that buffer. */
        q.noMoreGetsWillBeAdded = true;
        synchronized (q.pendingGets) {
            q.pendingGets.notifyAll();
        }
        q.log("stopping receiving thread.");
    }
}