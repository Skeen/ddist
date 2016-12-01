/**
 * Kode skrevet af;
 *
 * Hold 4; Gruppe: 2
 * Emil Madsen - 20105376
 * Rasmus - 20105109
 * Sverre - 20083549
 *
 * {skeen, emray, sverre}@cs.au.dk
 */

import multicastqueue.*;
import java.util.*;
import java.io.*;

/**
 * Takes a MultiCastQueue and pops the MulticastMessage's for printing to System.out.
 *
 * This only handles the following MulticastMessage's:
 * MulticastMessagePayload, MulticastMessageLeave and MulticastMessageJoin.
 */
class OutputThread extends Thread
{
    private MulticastQueue<String> queue;

    public OutputThread(MulticastQueue<String> queue)
    {
        // Initialise our message queue
        this.queue = queue;
    }

    public void run()
    {
        // Read the messages that are currently in the queue,
        // stopping when the queue is dead.
        MulticastMessage msg;
        while ((msg = queue.get()) != null)
        {
            handleCast(msg);
        }
    }

    /**
     * This is just a dispatcher: it will receive the incoming message
     * and call the respective handlers.
     * @param msg The incoming message that should be handled.
     */
    
    public static void handleCast(MulticastMessage msg)
    {
        if (msg instanceof MulticastMessagePayload)
        {
			MultiChat.log(msg);
            handle((MulticastMessagePayload) msg);
        }
        else if (msg instanceof MulticastMessageJoin)
        {
			MultiChat.log(msg);
            handle((MulticastMessageJoin) msg);
        }
        else if (msg instanceof MulticastMessageLeave)
        {
			MultiChat.log(msg);
            handle((MulticastMessageLeave) msg);
        }
    }

    private static void handle(MulticastMessagePayload msg)
    {
        // Get the text only, not the sender information too
        String payload = (String) msg.getPayload();
        System.out.println(msg.getSender() + " said: " + payload);
    }

    private static void handle(MulticastMessageJoin msg)
    {
        System.out.println("[" + msg.getSender() + " joined the chat group]");
    }

    private static void handle(MulticastMessageLeave msg)
    {
        System.out.println("[" + msg.getSender() + " left the chat group]");
    }
}