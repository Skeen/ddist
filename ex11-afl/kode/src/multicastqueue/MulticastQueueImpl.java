package multicastqueue;

import multicastqueue.fifo.MulticastQueueFifoOnly;
import multicastqueue.total.MulticastQueueTotal;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetSocketAddress;

/**
 * The implementation of the multicastqueue interface, loads internal
 * representation depending on the given DeliveryGuarantee
 */

public class MulticastQueueImpl<E extends Serializable> implements MulticastQueue<E>
{
    private MulticastQueue<E> queue = null;

    public MulticastQueueImpl(DeliveryGuarantee deliveryGuarantee)
    {
        switch(deliveryGuarantee)
        {
            case NONE:
            default:
            case FIFO:
                queue = new MulticastQueueFifoOnly<E>();
                break;
            case CAUSAL:
            case TOTAL:
                queue = new MulticastQueueTotal<E>();
        }
    }

    // defaults to FIFO
    public MulticastQueueImpl() {
        this(DeliveryGuarantee.FIFO);
    }

    public int createGroup(int port, DeliveryGuarantee deliveryGuarantee)
	    throws IOException
    {
        return queue.createGroup(port, deliveryGuarantee);
    }
	
	public int joinGroup(int port, 
			      InetSocketAddress knownPeer, 
			      DeliveryGuarantee deliveryGuarantee) 
	    throws IOException
    {
        return queue.joinGroup(port, knownPeer, deliveryGuarantee);
    }

	public void put(E object)
    {
        queue.put(object);
    }

	public MulticastMessage get()
    {
        return queue.get();
    }

	public void leaveGroup()
    {
        queue.leaveGroup();
    }

	public void run()
    {
        throw new UnsupportedOperationException("This should only be called from internal calls");
    }
}
