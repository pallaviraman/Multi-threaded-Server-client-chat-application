import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.IOException;
import java.net.Socket;
import java.net.ServerSocket;

/*
 * A chat server that delivers public and private messages.
 */
public class Server {

  // The server socket.
  private static ServerSocket serverSocket = null;
  // The client socket.
  private static Socket clientSocket = null;

  // This chat server can accept up to maxClientsCount clients' connections.
  private static final int maxClientsCount = 10;
  private static final clientThread[] threads = new clientThread[maxClientsCount];

  public static void main(String args[]) {

    // The default port number.
    int portNumber = 2222;
    if (args.length < 1) {
      System.out.println("Usage: java MultiThreadChatServerSync <portNumber>\n"
          + "Now using port number=" + portNumber);
    } else {
      portNumber = Integer.valueOf(args[0]).intValue();
    }

    /*
     * Open a server socket on the portNumber (default 2222). Note that we can
     * not choose a port less than 1023 if we are not privileged users (root).
     */
    try {
      serverSocket = new ServerSocket(portNumber);
    } catch (IOException e) {
      System.out.println(e);
    }

    /*
     * Create a client socket for each connection and pass it to a new client
     * thread.
     */
    while (true) {
      try {
        clientSocket = serverSocket.accept();
        int i = 0;
        for (i = 0; i < maxClientsCount; i++) {
          if (threads[i] == null) {
            (threads[i] = new clientThread(clientSocket, threads)).start();
            break;
          }
        }
        if (i == maxClientsCount) {
          PrintStream os = new PrintStream(clientSocket.getOutputStream());
          os.println("Server too busy. Try later.");
          os.close();
          clientSocket.close();
        }
      } catch (IOException e) {
        System.out.println(e);
      }
    }
  }
}

/*
 * The chat client thread. This client thread opens the input and the output
 * streams for a particular client, ask the client's name, informs all the
 * clients connected to the server about the fact that a new client has joined
 * the chat room, and as long as it receive data, echos that data back to all
 * other clients. The thread broadcast the incoming messages to all clients and
 * routes the private message to the particular client. When a client leaves the
 * chat room this thread informs also all the clients about that and terminates.
 */
class clientThread extends Thread {

  private String clientName = null;
  private DataInputStream is = null;
  private PrintStream os = null;
  private Socket clientSocket = null;
  private final clientThread[] threads;
  private int maxClientsCount;

  public clientThread(Socket clientSocket, clientThread[] threads) {
    this.clientSocket = clientSocket;
    this.threads = threads;
    maxClientsCount = threads.length;
  }

  @SuppressWarnings("deprecation")
public void run() {
    int maxClientsCount = this.maxClientsCount;
    clientThread[] threads = this.threads;

    try {
      /*
       * Create input and output streams for this client.
       */
      is = new DataInputStream(clientSocket.getInputStream());
      os = new PrintStream(clientSocket.getOutputStream());
      String name;
      while (true) {
        os.println("Enter name");
        name = is.readLine().trim();
        System.out.println("Client " + name + " connected");
        if (name!=null) {
          break;
        }
      }

  
      synchronized (this) {
        for (int i = 0; i < maxClientsCount; i++) {
          if (threads[i] != null && threads[i] == this) {
            clientName = name;
            break;
          }
        }
      }
      /* Start the conversation. */
      while (true) {
        String line = is.readLine();
        if (line.startsWith("/quit")) {
          break;
        }
        /* If the message is private sent it to the given client. */
        if (line.startsWith("unicast")) {
          String[] words = line.split("\\s", 0);
          if (words.length > 1 && words[1].equals("message")) {
            //words[1] = words[1].trim();
            if (!words[words.length-1].isEmpty()) {
              synchronized (this) {
            	  String msg = "";
            	  for(int j = 2; j < words.length-1;j++)
            	  {
            		  msg = msg	+ words[j];
            	  }
                for (int i = 0; i < maxClientsCount; i++) {
                  if (threads[i] != null && threads[i] != this
                      && threads[i].clientName != null
                      && threads[i].clientName.equals(words[words.length-1])) {
                	 
                	  threads[i].os.println("@" + name + ": " + msg);
                    /*
                     * Echo this message to let the client know the private
                     * message was sent.
                     */
                   // this.os.println(">" + name + "> " + words[1]);
                    break;
                  }
                }
                System.out.println(name +" unicast message to " + words[words.length-1]);
              }
            }
          }else{
        	  synchronized (this) {
        		  for (int i = 0; i < maxClientsCount; i++) {
                      if (threads[i] != null && threads[i] != this
                          && threads[i].clientName != null
                          && threads[i].clientName.equals(words[words.length-1])) {
                    	  int index = words[2].lastIndexOf("/");
                    	  String filename = words[2].substring(index+1);
                    	  FileInputStream in = new FileInputStream(words[2]);
                    	  String target = "../"+threads[i].clientName+"/"+filename;
                    	  FileOutputStream out = new FileOutputStream(target);
                    	  
                          // Copy the bits from instream to outstream
                          byte[] buf = new byte[1024];
                          int len;
                          while ((len = in.read(buf)) > 0) {
                              out.write(buf, 0, len);
                          }
                          out.flush();
                          in.close();
                          out.close();
                          threads[i].os.println("File " + filename + " was sent by " + name);
                      }
        	  }
        		  System.out.println(name +" unicast file to " + words[words.length-1]);
          }
        }
        }else if (line.startsWith("broadcast")){
          /* The message is public, broadcast it to all other clients. */
        	String[] words = line.split("\\s", 3);
        	if (words.length > 1 && words[1].equals("message") && words[2] != null) {
        		synchronized (this) {
                  for (int i = 0; i < maxClientsCount; i++) {
                      if (threads[i] != null && threads[i].clientName != null && threads[i] != this) {
                       threads[i].os.println("@" + name + ": " + words[2]);
                      }
                    }
                  System.out.println(name + " broadcasted message");
        	  }

          }
        	else{
          	  synchronized (this) {
          		  for (int i = 0; i < maxClientsCount; i++) {
          			if (threads[i] != null && threads[i].clientName != null && threads[i] != this) {
                      	  int index = words[2].lastIndexOf("/");
                      	  String filename = words[2].substring(index+1);
                      	  FileInputStream in = new FileInputStream(words[2]);
                      	  String target = "../"+threads[i].clientName+"/"+filename;
                      	  FileOutputStream out = new FileOutputStream(target);
                      	  
                            // Copy the bits from instream to outstream
                            byte[] buf = new byte[1024];
                            int len;
                            while ((len = in.read(buf)) > 0) {
                                out.write(buf, 0, len);
                            }
                            out.flush();
                            in.close();
                            out.close();
                            threads[i].os.println("File " + filename + " was sent by " + name);
                        }
          	  }
          		 System.out.println(name + " broadcasted file");
            }
          }
        }
        else{
            String[] words = line.split("\\s", 0);
            if (words.length > 1 && words[1].equals("message")) {
              //words[1] = words[1].trim();
              if (!words[words.length-1].isEmpty()) {
                synchronized (this) {
              	  String msg = "";
              	  for(int j = 2; j < words.length-1;j++)
              	  {
              		  msg = msg	+ " " +words[j];
              	  }
                  for (int i = 0; i < maxClientsCount; i++) {
                    if (threads[i] != null && threads[i] != this
                        && threads[i].clientName != null
                        && !(threads[i].clientName.equals(words[words.length-1]))) {
                  	 
                  	  threads[i].os.println("@" + name + ": " + msg);
                      /*
                       * Echo this message to let the client know the private
                       * message was sent.
                       */
                      break;
                    }
                  }
                  System.out.println(name + " blockcast message excluding " + words[words.length-1]);
                }
              }
            }
            else{
          	  synchronized (this) {
          		  for (int i = 0; i < maxClientsCount; i++) {
                      if (threads[i] != null && threads[i] != this
                              && threads[i].clientName != null
                              && !(threads[i].clientName.equals(words[words.length-1]))) {
                      	  int index = words[2].lastIndexOf("/");
                      	  String filename = words[2].substring(index+1);
                      	  FileInputStream in = new FileInputStream(words[2]);
                      	  String target = "../"+threads[i].clientName+"/"+filename;
                      	  FileOutputStream out = new FileOutputStream(target);
                      	  
                            // Copy the bits from instream to outstream
                            byte[] buf = new byte[1024];
                            int len;
                            while ((len = in.read(buf)) > 0) {
                                out.write(buf, 0, len);
                            }
                            in.close();
                            out.close();
                            threads[i].os.println("File " + filename + " was sent by " + name);
                        }
          	  }
          		 System.out.println(name + " blockcast file excluding " + words[words.length-1]);
            }
          }
          } 
      }
      synchronized (this) {
        for (int i = 0; i < maxClientsCount; i++) {
          if (threads[i] != null && threads[i] != this
              && threads[i].clientName != null) {
            threads[i].os.println("*** The user " + name
                + " is leaving the chat room !!! ***");
          }
        }
      }
      os.println("*** Bye " + name + " ***");

      /*
       * Clean up. Set the current thread variable to null so that a new client
       * could be accepted by the server.
       */
      synchronized (this) {
        for (int i = 0; i < maxClientsCount; i++) {
          if (threads[i] == this) {
            threads[i] = null;
          }
        }
      }
      /*
       * Close the output stream, close the input stream, close the socket.
       */
      is.close();
      os.close();
      clientSocket.close();
    } catch (IOException e) {
    }
  }
}
