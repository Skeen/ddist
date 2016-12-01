package multicastqueue;

import java.net.InetSocketAddress;

/**
 * Returned by MulticastQueue to signal that a peer has left the peer
 * group.  A peer has not officially left the peer group until it
 * itself received its own leave message.
 *
 * @author Jesper Buus Nielsen, Aarhus University, 2012.
 */
public class MulticastMessageLeave extends MulticastMessage {
    public MulticastMessageLeave(InetSocketAddress sender) {
	super(sender);
    }
    public String toString() {
	return "(LeaveMessage from " + getSender() + ")";
    }
}
