import java.net.*;
import java.io.*;
import java.util.*;

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

    // The relative location where files are to be served from
    private static final String fileLocation = "FilesToBeServed/";

    /**
     * Constructs the WebServerThread
     * @param client The client to be served by this thread
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
        // Print who's requested this connection.
        System.out.println(
                "Connection from " + client.getInetAddress() + ":" + client.getPort());

        // The list of strings in the request
        List<String> request = null;
        // Attempt to read the request
        try
        {
            request = readRequest();
        }
        catch(IOException e)
        {
            e.printStackTrace();
            return;
        }

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
            try
            {
                //400=Bad request
                sendResponse(400, "<b>The HTTP request appeared malformed, please try again later</b>");
            }    
            catch(IOException e)
            {
                e.printStackTrace();
            }
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
            e.printStackTrace();
        }
    }

    /**
     * Reads the entire request into a list of strings. 
     * Any later reads of the input stream will return EOF.
     * @return a list of strings, that is the loaded request
     * @throws java.io.IOException
     */
    private List<String> readRequest()
        throws IOException
    {
        // Initialize our list of strings
        List<String> requestStrings = new ArrayList<String>();

        // And make the Reader ready to recieve the clients input stream
        BufferedReader clientRequestStream = new BufferedReader(
                                             new InputStreamReader(
                                                 client.getInputStream()));  

        // Read the entire request
        String line;
        // Read the next line
        while ((line = clientRequestStream.readLine()) != null)
        {
            if (line.trim().equals(""))
                break;

            // Add it to the list of strings
            requestStrings.add(line);
            // And possibly debug print it
            // System.out.println("DEBUG: " + line);
        }
        
        // Close for more input
        client.shutdownInput();        
        
        return requestStrings;
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
        System.out.println("DEBUG: httpMethod: " + httpMethod);
        System.out.println("DEBUG: httpQueryString: " + httpRequestURI);
        if (isFullRequest) {
            System.out.println("DEBUG: HTTP-version: " + httpVersion);
        }


        /* Now handle the request based on the method token */

        // If we got a get request
        if(httpMethod.equals("GET"))
        {
            // If the query was for the default page
            if (httpRequestURI.equals("/")) 
            {
                // Simply redirect it
                httpRequestURI = "/index.html";
            }

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
                sendResponse(200, file);
            }    
            catch(IOException e)
            {
                e.printStackTrace();
            }
        }
        else
        { 
            // Send 404=Not found
            try
            {
                sendResponse(404, "<b>The Requested resource not found ....</b>");
            }    
            catch(IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    private void sendResponse(int statusCode, File file)
        throws IOException
    {
        sendResponse(statusCode,"",file);
    }

    private void sendResponse(int statusCode, String responseString)
            throws IOException
    {
        sendResponse(statusCode,responseString,null);
    }

    /**
     * This is the function that actually sends the response back, according to
     * its parameters.
     * If file is null, the message is send instead.
     * If file doesn't end in htm/html it's send as a binary block, otherwise
     * it's send as text/html.
     * @param statusCode The status code of the transfer, 200=OK, 400=Bad request, 404=NotFound.
     * @param responseString The string to be passed to the client, if file is null
     * @param file The file to be send to the client, if any.
     * @throws java.io.IOException
     */
    private void sendResponse(int statusCode, String responseString, File file)
        throws IOException 
    {
        // The Status-Line needs to be conforming and look like this:
        // "HTTP/" 1*DIGIT "." 1*DIGIT SP 3DIGIT SP
        // It's bnf is;
        // Status-Line = HTTP-Version SP Status-Code SP Reason-Phrase CRLF
        String statusLine = null;
        String httpVersion = "HTTP/1.0";
        String reasonPhrase = null;
        
        // Set the Status-Line to be send, dependent on the Status-Code.
        // This needs to conform with the standard.

        // The Status-Code is a part of the Status-Line, and has the message
        // corresponding with the Status-Code send with it;

        // Examples of the ones we use are below; These are described in rfc
        // 1945, 6.1.1

        if (statusCode == 200)
        {
            reasonPhrase = "OK";
        }
        else if (statusCode == 404)
        {
            reasonPhrase = "Not Found";
        }
        else //400
        {
            reasonPhrase = "Bad request";
        }

        statusLine = httpVersion + " " + statusCode + " " + reasonPhrase + "\r\n";

        // The kind of content we're sending back
        // This needs to conform with the standard as well.
        // In accordance with 'rfc1945' the contentType is of the following BNF;
        //    Content-Type   = "Content-Type" ":" media-type
        String contentTypeLine = "";
        // The contentLength, is supposed to hold the length, that indicates the
        // size of the response. (rfc1945 7.2.2)
        String contentLengthLine;
        if (file == null) 
        {
            // Figure out the HTML Message to return
            responseString =    "<!DOCTYPE html>" +
                                "<html>" +
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
            // Figure out the length of the message
            contentLengthLine = "Content-Length: " + file.length() + "\r\n";
            // Check if the file is a .htm/.html or a text file
            if (file.getName().endsWith(".htm")
                    || file.getName().endsWith(".html")
                    || file.getName().endsWith(".txt"))
            {
                // In accordance with 'rfc1945' the contentType, the html pages
                // content-type is "text/html"
                contentTypeLine = "Content-Type: text/html\r\n";
            }
            // Check if the file is a .pdf
            if (file.getName().endsWith(".pdf"))
            {
                // In accordance with 'rfc3778', the pdf content-type is "application/pdf"
                contentTypeLine = "Content-Type: application/pdf\r\n";
            }
            // Otherwise don't send the content-type
            else
            {
                // Simply leave it to the browser to figure
                contentTypeLine = "Content-Type: \r\n";	
            }
        }

        // Open the output stream to the client, it is with this stream we're
        // going to send our response.
        DataOutputStream clientResponseStream = new DataOutputStream(client.getOutputStream());

        // Write out the answer to the client
        // This somewhat follows the bnf in rfc1945 4.1
        clientResponseStream.writeBytes(statusLine);
        clientResponseStream.writeBytes(contentTypeLine);
        clientResponseStream.writeBytes(contentLengthLine);
        // Let the browser know, we don't anymore requests on this socket.
        // This obviously need to conform to standard, and in our web server
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
            // Otherwise send the file
            sendFile(file, clientResponseStream);
        }

        // Close for more output
        client.shutdownOutput();
    }

    /**
     * Reads a file from the disk, and sends it to the provided output stream.
     * @param file the file to be transferred
     * @param out the output stream to flush the file out through.
     * @throws java.io.IOException
     */
    private void sendFile(File file, DataOutputStream out)
        throws IOException 
    {
        // Open a file input stream
        FileInputStream fin = new FileInputStream(file);

        // byte buffer, to increase disk performance
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
