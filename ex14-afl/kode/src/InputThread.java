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

import multicastqueue.MulticastQueue;
import java.io.*;

/**
 * Takes a MultiCastQueue and pushes new MulticastMessage's on it.
 *
 * The MulticastMessages are created via userInput from System.in.
 */
class InputThread extends Thread
{
    private MulticastQueue<String> queue;
    private BufferedReader reader;

    public InputThread(MulticastQueue<String> queue)
    {
        // Initialise our message queue
        this.queue = queue;
        reader = new BufferedReader(new InputStreamReader(System.in));
    }

    public void run()
    {
		try
		{
            // At this point the queue should be connected, and we should be
            // able to start writing to it.

            String line;
            // Keep going while, there's more input!
            while( (line = reader.readLine()) != null)
            {
                // Check the line's content
                // An instance of MultiChat should disconnect from the group if
                // the user types "exit" followed by ENTER. 
                if(line.equals("exit"))
                {
                    // Leave the group and break
                    queue.leaveGroup();
                    System.out.println("Exiting chat.");
                    break;
                }
                else if(!line.trim().equals(""))
                {
                    // Send the nonempty line to the queue
                    queue.put(line);
                }
            }
		}
		catch (Exception e)
		{
			// Print the StackTrace
			e.printStackTrace();
		}
        
        //System.exit(1);
    }
}
