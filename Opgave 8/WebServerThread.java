import java.net.*;
import java.io.*;
import java.util.*;

/* ISSUES */
/** 
 * WebServerThread.java:149:
 *      warning: [deprecation] decode(String) in URLDecoder has been deprecated
 *          filename = URLDecoder.decode(filename);
 */

/**
 * The WebServerThread class has the responsability of serving requests.
 * Essencially this means, that it does not listen for client,
 * it just serves them, listening for clients is the role of the WebServer
 * class itself.
 *
 * The design is in no way optimized for performence, but is designed for
 * readablity.
 */
class WebServerThread extends Thread 
{
    // The client we're going to serve
    private Socket client;
    // The location of which files are to be served from
    private static final String fileLocation = "FilesToBeServed/";

    /**
     * Constructs the WebServerThread
     * @param client The client to be served, by this thread
     */
    public WebServerThread(Socket client)
    {
        this.client = client;
    }

    /**
     * Serves the client seeded in the constructor
     */
    public void run()
    {
        // The list of strings in the request
        List<String> request = null;
        // Attempt to read the request
        try
        {
            request = readRequest();
        }
        catch(IOException e)
        {
            System.out.println(e);
            e.printStackTrace();
            return;
        }
        // If no request was read, simply give up
        // request will never be null in the second test, due to the first one
        // and since we're using the 'or' operator.
        if(request == null || request.size() == 0)
        {
            // We could be nice and send a response back, stating we need the
            // client to try again, or we could ignore it, and let the browser
            // figure it out itself.
            //
            // Code for sending the bad request information is below:
            /*
            try
            {
                //400=Bad request
                sendResponse(400, "<b>The HTTP request appeared malform, please try again later", null);
            }    
            catch(IOException e)
            {
                System.out.println(e);
                e.printStackTrace();
            }
            */
        }
        else
        {
            // Parse the request
            String filename = parseRequest(request);
            // Serve the file
            serveFileRequest(filename);
        }
        // Close off the socket, in order to flush/send the response if needed
        try
        {
            client.close();
        }
        catch(IOException e)
        {
            System.out.println(e);
            e.printStackTrace();
        }
    }

    /**
     * Reads the entire request into a list of strings
     * @return a list of strings, that is the loaded request
     */
    private List<String> readRequest()
        throws IOException
    {
        // Initialize our list of strings
        List<String> requestStrings = new ArrayList<String>();
        // And make the reader ready, notice it's reading the clients input stream
        BufferedReader clientRequestStream = new BufferedReader(
                                             new InputStreamReader(
                                                 client.getInputStream()));  

        // Read the entire request
        while (clientRequestStream.ready())
        {
            // Read the next line
            String line = clientRequestStream.readLine();
            // And it to the list of strings
            requestStrings.add(line);
            // And possibly debug print it
            // System.out.println("DEBUG: " + line);
        }
        
        // Close for more input
        client.shutdownInput();        
        
        return requestStrings;
    }

    /**
     * Attempt to parse the request, in order to figure out which file is requested
     * @param request A list of strings, that is the entire request
     * @return The name of the requested file, if any, otherwise the empty
     * string.
     */
    private String parseRequest(List<String> request)
    {
        // The first line of the request is the header line
        String headerLine = request.get(0);
        // We'll break this one into tokens
        StringTokenizer tokenizer = new StringTokenizer(headerLine);
        // This first one of these, is the 'GET' and the next is the file
        String httpMethod = tokenizer.nextToken();
        String httpQueryString = tokenizer.nextToken();

        // Debug print the method and query string

        // If we got a get request
        if(httpMethod.equals("GET"))
        {
            // If the query was for the default page
            if (httpQueryString.equals("/")) 
            {
                // Simply redirect it
                httpQueryString = "/index.html";
            }
            // The get request should now ask for a file.
            // However we'll need to get rid of the first '/'
            String filename = httpQueryString.substring(1);
            // The string is possibly URL encoded, we'll decode it.
            filename = URLDecoder.decode(filename);
            // Return the string to the wanted file
            return filename;
        }
        // Otherwise if we didn't get a request, return the empty string.
        return "";
    }

    /**
     * Serves a file request, by sending the file back, if it exists.
     * @param filename The name of the requested file.
     */
    private void serveFileRequest(String filename)
    {
        // Check if the file exists
        File file = new File(fileLocation + filename);
        // If it does, and it's a valid file;
        if (file.isFile() && file.exists())
        {
            // Then send it back
            try
            {
                //200 = OK, "" = no message
                sendResponse(200, "", file);
            }    
            catch(IOException e)
            {
                System.out.println(e);
                e.printStackTrace();   
            }
            return;
        }
        else
        { 
            // Send 404
            try
            {
                //404=Not found
                sendResponse(404, "<b>The Requested resource not found ....", null);
            }    
            catch(IOException e)
            {
                System.out.println(e);
                e.printStackTrace();
            }
            return;
        }
    }

    /**
     * This is the function that actually sends the response back, according to
     * its parameters.
     * If file is null, the message is send instead.
     * If file doesn't end in htm/html it's send as a binary block, otherwise
     * it's send as text/html.
     * @param statusCode The status code of the transfer, 200=OK, 400=Bad request, 404=NotFound.
     * @param responseString The string to be passed to the client, if file is null
     * @param File The file to be send to the client, if any.
     */
    private void sendResponse(int statusCode, String responseString, File file)
        throws IOException 
    {
        String statusLine = null;
        
        // Set the statusline to be send, dependend on the statuscode.
        // This needs to conform with the standard.
        if (statusCode == 200)
        {
            statusLine = "HTTP/1.1 200 OK" + "\r\n";
        }
        else if (statusCode == 404)
        {
            statusLine = "HTTP/1.1 404 Not Found" + "\r\n";	
        }
        else //400
        {
            statusLine = "HTTP/1.1 400 Bad request" + "\r\n";	
        }

        // Default to the fact that we're sending text/html
        // This needs to conform with the standard as well.
        String contentTypeLine = "Content-Type: text/html" + "\r\n";
        // The contentLength, is supposed to hold the length, that indicates the
        // size of the response.
        String contentLengthLine = null;
        if (file == null) 
        {
            // Figure out the HTML Message to return
            responseString =    "<html>" +
                                    "<head>" +
                                        "<title>HTTP Server in java</title>" +
                                    "</head>" +
                                    "<body>" +
                                        responseString +
                                    "</body>" +
                                "</html>";
            // Figure out the length of the message
            contentLengthLine = "Content-Length: " + responseString.length() + "\r\n";
        }
        else
        {
            // Open a file input stream
            FileInputStream fin = new FileInputStream(file);
            // Figure out the length of the message
            // TODO: fin.avilable() will not work! - Check the java docs
            contentLengthLine = "Content-Length: " + Integer.toString(fin.available()) + "\r\n";
            // Check if the file is a .htm/.html
            if (!file.getName().endsWith(".htm") && !file.getName().endsWith(".html"))
            {
                // Simply leave it to the browser to figure
                contentTypeLine = "Content-Type: \r\n";	
            }
            // Close the file
            fin.close();
        }

        // Open the output stream to the client, it is with this stream we're
        // going to send our response.
        DataOutputStream clientResponseStream = new DataOutputStream(client.getOutputStream());

        // Write out the answer to the client
        clientResponseStream.writeBytes(statusLine);
        // This should contain nice information about the server
        //clientResponseStream.writeBytes(serverdetails);
        clientResponseStream.writeBytes(contentTypeLine);
        clientResponseStream.writeBytes(contentLengthLine);
        // Let the browser know, we don't anymore requests on this socket.
        // This obviously need to conform to standard, and in our webserver
        // design its just easier to let the browser request every file on a new
        // socket.
        clientResponseStream.writeBytes("Connection: close\r\n");
        clientResponseStream.writeBytes("\r\n");	

        // Send the file if any.
        if (file == null)
        {
            // If no file, send the response message
            clientResponseStream.writeBytes(responseString);
        }
        else
        {
            // Oterwise send the file
            sendFile(file, clientResponseStream);
        }

        // Close for more output
        client.shutdownOutput();
    }

    /**
     * Reads a file from the disk, and sends it to the provided output stream.
     * @param file the file to be transfered
     * @param out the output stream to flush the file out through.
     */
    private void sendFile(File file, DataOutputStream out)
        throws IOException 
    {
        // Open a file input stream
        FileInputStream fin = new FileInputStream(file);

        // byte buffer, to increase disk performence
        byte[] buffer = new byte[1024];
        int bytesRead;
        
        // Transfer the entire file.
        while ((bytesRead = fin.read(buffer)) != -1 ) 
        {
            // Write all of the read bytes to the stream
            out.write(buffer, 0, bytesRead);
        }
        
        // Close the file
        fin.close();
    }
}
