package multicastqueue;

import java.io.Serializable;
import java.net.InetSocketAddress;

/**
 * This class of objects returned by MulticastQueue to signal
 * that an object was multicast. It has a generic type E, which
 * specified that class of the object that was multicast.
 *
 * @author Jesper Buus Nielsen, Aarhus University, 2012.
 */
public class MulticastMessagePayload<E extends Serializable> extends MulticastMessage {
    private E payload;
    /**
     * @param e the object that is/was multicast.
     */
    public MulticastMessagePayload(InetSocketAddress sender, E e) {
	super(sender);
	payload = e;
    }
    /**
     * @return The payload of this message.
     */
    public E getPayload() {
	return payload;
    }
    public String toString() {
	return "(PayloadMessage from " + getSender() + 
	    " with payload " + payload + ")";
    }
}
