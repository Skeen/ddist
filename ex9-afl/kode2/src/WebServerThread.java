import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

/**
 * The WebServerThread class has the responsibility of serving requests.
 * Essentially this means, that it does not listen for client,
 * it just serves them, sending them responses.
 * Listening for clients is the responsibility of the WebServer
 * class itself.
 *
 * The design is in no way optimized for performance, but is designed for
 * readability.
 */
class WebServerThread extends Thread 
{
    // The sole client this thread is going to serve
    private final Socket client;
    private final RPCRequestServer requestServer;
    private final TransferHelper transferHelper;

    /**
     * Constructs the WebServerThread
     * @param client The client to be served by this thread
     */
    public WebServerThread(Socket client, RPCRequestServer requestServer)
    {
        this.client = client;
        this.requestServer=requestServer;
        transferHelper = new TransferHelper(client);
    }

    /**
     * Serves the client seeded in the constructor
     */
    public void run()
    {
        // Print who's requested this connection.
        System.out.println(
                "Connection from " + client.getInetAddress() + ":" + client.getPort());

        // The list of strings in the request
        List<String> request;
        // Attempt to read the request
        try
        {
            request = transferHelper.readRequest();

            // If no request was read, simply give up
            // request will never be null in the second test,
            // due to the first one and since we're using the
            // 'or' operator.
            if(request == null || request.size() == 0)
            {
                // We could be nice and send a response back, stating we need the
                // client to try again, or we could ignore it, and let the browser
                // figure it out itself. We'll try to send back the bad request
                // response as below
                //
                // Code for sending the bad request information is below:
                //400=Bad request
                transferHelper.sendResponse(400, "<b>The HTTP request appeared malformed, please try again later</b>");
            }
            else
            {
                // Parse the request
                String requestString = parseRequest(request);
                // Serve the request
                requestServer.serveRequest(requestString,transferHelper);
            }
            // Close off the socket, in order to flush/send the response if needed
            client.close();
        }
        catch(IOException e)
        {
            e.printStackTrace();
            System.out.println("a printout appeared...?");
        }
    }

    /**
     * Attempt to parse the request, in order to figure out which file is
     * requested.
     * Please see rfc1945 section 5 for details on the proper structure of
     * requests.
     * @param request A list of strings, that is the entire request.
     * @return The name of the requested file, if any, otherwise just the empty
     * string.
     */
    private String parseRequest(List<String> request)
    {
        // The first line of the request should be the Request-Line
        String httpRequestLine = request.get(0);

        // We'll break this one into tokens
        StringTokenizer tokenizer = new StringTokenizer(httpRequestLine, " ");

        // Let us trace if we have a Full-Request or a Simple-Request.
        boolean isFullRequest = true;

        // This first one of these, is the 'GET' and the next is the file
        String httpMethod = tokenizer.nextToken();
        String httpRequestURI = tokenizer.nextToken();

        // The third is the HTTP-version which is not given in HTTP/0.9
        String httpVersion = null;
        try
        {
            httpVersion = tokenizer.nextToken();
        }
        catch (NoSuchElementException e)
        { 
            // This happens when the Request is not a Full-Request.
            // as a Full-Request would hold the HTTP-version in the Request-Line
            isFullRequest = false;
        }

        // Debug print the method and query string
        System.out.println("Debug: Received and parsed Request-Line: \r\n   "
                + httpMethod+" "+httpRequestURI + (isFullRequest ?" "+httpVersion :""));


        /* Now handle the request based on the method token */

        // If we got a get request
        if(httpMethod.equals("GET"))
        {
            // The GET request should now ask for a file.
            // However we'll need to get rid of the first '/'
            String filename = httpRequestURI.substring(1);

            // The string is possibly URL encoded, we'll decode it.
            try
            {
                filename = URLDecoder.decode(filename,"UTF-8");
            } catch (UnsupportedEncodingException e)
            {
                // This should not happen as UTF-8 is a supported encoding.
                e.printStackTrace();
            }

            // Return the string to the wanted file
            return filename;
        }
        // Otherwise if we didn't get a GET-request, return the empty string.
        return "";
    }
}
