package multicastqueue.total;

import multicastqueue.MulticastMessage;

import java.net.InetSocketAddress;

/**
* This class of objects is used to send the address of the
* existing peers to a newly joined member. Happens in response to
* a join relay message. Made as a static class to avoid the
* serialization considers the outer class.
*/
class WelcomeMessage extends MulticastMessage
{
public WelcomeMessage(InetSocketAddress myAddress)
{
    super(myAddress);
}

public String toString()
{
    return "(WelcomeMessage from " + getSender()  + ")";
}
}
