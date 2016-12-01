import java.io.*;
import java.net.Socket;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

public class TransferHelper {
    private final Socket client;

    public TransferHelper(Socket client) {
        this.client=client;
    }

    public void sendResponse(int statusCode, String responseString)
            throws IOException {
        sendResponse(statusCode, responseString, null);
    }

    /**
     * This is the function that actually sends the response back, according to
     * its parameters.
     * If file is null, the message is send instead.
     * If file doesn't end in htm/html it's send as a binary block, otherwise
     * it's send as text/html.
     *
     * @param statusCode     The status code of the transfer, 200=OK, 400=Bad request, 404=NotFound.
     * @param responseString The string to be passed to the client, if file is null
     * @param file           The file to be send to the client, if any.
     * @throws java.io.IOException If an I/O error occurs.
     */
    void sendResponse(int statusCode, String responseString, File file)
            throws IOException {
        // According to rfc1945, the BNF for a Status-Line is this:
        // Status-Line = HTTP-Version SP Status-Code SP Reason-Phrase CRLF
        String statusLine;
        String httpVersion = "HTTP/1.0";
        String reasonPhrase;

        if (statusCode == 200) {
            reasonPhrase = "OK";
        } else if (statusCode == 404) {
            reasonPhrase = "Not Found";
        } else //400
        {
            reasonPhrase = "Bad request";
        }

        statusLine = httpVersion + " " + statusCode + " " + reasonPhrase + "\r\n";

        // The kind of content we're sending back:
        // In accordance with 'rfc1945' the contentType is of the following BNF;
        //    Content-Type   = "Content-Type" ":" media-type
        String contentTypeLine = "Content-Type: text/html\r\n";

        // The contentLength is supposed to hold the length indicating the
        // size of the response. (rfc1945 7.2.2)
        String contentLengthLine;
        if (file == null) {
            // Figure out the HTML Message to return
            responseString = "<!DOCTYPE html>" +
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
        } else {
            // Figure out the length of the message
            contentLengthLine = "Content-Length: " + file.length() + "\r\n";
            // throws SecurityException if a security manager exists and its denies read access to the file

            // Check if the file is a .htm/.html or a text file
            String filename = file.getName();
            if (filename.endsWith(".htm")
                    || filename.endsWith(".html")
                    || filename.endsWith(".txt")) {
                // Default value already applies
            }
            // Check if the file is a .pdf
            else if (filename.endsWith(".pdf")) {
                // In accordance with 'rfc3778', the pdf content-type is "application/pdf"
                contentTypeLine = "Content-Type: application/pdf\r\n";
            }
            // Otherwise don't send the content-type
            else {
                // Make a guess from the file-type and leave the browser to figure out,
                // if it is a good guess or not.
                contentTypeLine = "Content-Type: "
                        + URLConnection.guessContentTypeFromName(filename)
                        + "\r\n";
            }
        }

        // Open the output stream to the client.
        // It is with this stream we're going to send our response.
        DataOutputStream clientResponseStream = new DataOutputStream(client.getOutputStream());
        // Throws IOException if an I/O error occurs when creating the output stream
        // or if the socket is not connected

        // Write out the answer to the client
        // This somewhat follows the bnf in rfc1945 4.1
        clientResponseStream.writeBytes(statusLine);
        clientResponseStream.writeBytes(contentTypeLine);
        clientResponseStream.writeBytes(contentLengthLine);
        // throws IOException if an I/O error occurs.

        // Let the browser know, we don't request anymore on this socket.
        // This obviously need to conform to standard, and in our web server
        // design its just easier to let the browser request every file on a new
        // socket.
        clientResponseStream.writeBytes("Connection: close\r\n");
        clientResponseStream.writeBytes("\r\n");
        // throws IOException if an I/O error occurs.

        // Send the file if any.
        if (file == null) {
            // If no file, send the response message
            clientResponseStream.writeBytes(responseString);
        } else {
            // Otherwise send the file
            sendFile(file, clientResponseStream);
            // throws IOException if an I/O error occurs.
        }

        // Close for more output
        client.shutdownOutput();
    }

    /**
     * Reads a file from the disk, and sends it to the provided output stream.
     * @param file the file to be transferred
     * @param out the output stream to flush the file out through.
     * @throws java.io.IOException If an I/O error occurs.
     */
    private void sendFile(File file, DataOutputStream out)
            throws IOException
    {
        // Open a file input stream
        FileInputStream fin = new FileInputStream(file);
        // throws FileNotFoundException if the file can not be opened

        // byte buffer, to increase disk performance
        byte[] buffer = new byte[1024];
        int bytesRead;

        // Transfer the entire file.
        while ((bytesRead = fin.read(buffer)) != -1 )
        {
            // Write all of the read bytes to the stream
            out.write(buffer, 0, bytesRead);
            // throws IOException if an I/O error occurs.
        }

        // Close the file
        fin.close();
    }

    /**
     * Reads the entire request into a list of strings.
     * Any later reads of the input stream will return EOF.
     * @return a list of strings, that is the loaded request
     * @throws java.io.IOException if an I/O error occurs.
     */
    public List<String> readRequest()
            throws IOException
    {
        // Initialize our list of strings
        List<String> requestStrings = new ArrayList<String>();

        // And make the Reader ready to receive the clients input stream
        BufferedReader clientRequestStream = new BufferedReader(
                new InputStreamReader(
                        client.getInputStream()));
        //Throws: IOException - if an I/O error occurs when creating the input stream, the socket is closed,
        // the socket is not connected, or the socket input has been shutdown using shutdownInput()

        // Read the entire request
        String line;
        // Read the next line
        while ((line = clientRequestStream.readLine()) != null) // Throws: IOException - If an I/O error occurs
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
}