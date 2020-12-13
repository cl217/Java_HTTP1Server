import java.io.*;
import java.lang.Thread;
import java.net.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.w3c.dom.Text;


public class ClientThread extends Thread{

    public Socket clientSocket;
    //int debug = 26;
    //int testcase = -1;
    
    /** Initializers */
    public ClientThread(){}
    public ClientThread(Socket clientSocket){
        this.clientSocket = clientSocket;
    }
    public void setSocket(Socket clientSocket){
    	this.clientSocket = clientSocket;
    }


	/**Main thread method */
    public void run(){

        //testcase = HTTP1Server.testcase;

        //System.out.println(testcase);

        //Create input stream and read from client
        ArrayList<String> request = new ArrayList<String>();

        //println("\n=============================\n");
        //println("Test Case " + testcase + "\n");
        
        //println("# Request:");



        try{
        	clientSocket.setSoTimeout(5000);
            BufferedReader inFromClient = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            
            System.out.println("====New Request====");
            do{
                String line = inFromClient.readLine();
                request.add(line);
                System.out.println(line);
            }while(inFromClient.ready());
            System.out.println();

            /*
            String line;
            while (!(line = inFromClient.readLine()).isEmpty()) {
                request.add(line);
                System.out.println(line);
            }
            if(line.isEmpty()){
                System.out.println("<Empty line>");
                System.out.println("Has next line: " + inFromClient.ready());
            }
            */


        }catch(SocketException e){
        	writeToClient("HTTP/1.0 408 Request Timeout", null, true);
        	return;
        }catch(SocketTimeoutException e){
        	writeToClient("HTTP/1.0 408 Request Timeout", null, true);
        	return;
        }catch(IOException e){
            //System.out.println("46 Error 500");
            writeToClient("HTTP/1.0 500 Internal Server Error", null, true);
            return;
        }

        //println();
        //println("# Debug:");


        //Parse client request
        String [] parseRequest = request.get(0).split(" ");
        if(parseRequest.length != 3 ){
            writeToClient("HTTP/1.0 400 Bad Request", null, true);
            return;
        }
        String command = parseRequest[0];
        String resource = parseRequest[1];
        String version = parseRequest[2];

        //Handle error for versioning
        if(!version.substring(0, 5).equals("HTTP/")){ 
            writeToClient("HTTP/1.0 400 Bad Request", null, true);
            return;
        }
        try{
            String versionNo = version.replace("HTTP/", "");
            if( versionNo.equals("") ){ 
                writeToClient("HTTP/1.0 400 Bad Request", null, true);
                return;
            }
            if( Double.parseDouble(versionNo) > 1.0 ){ 
                writeToClient("HTTP/1.0 505 HTTP Version Not Supported", null, true);
                return;
            }
        }catch(NumberFormatException e){
            writeToClient("HTTP/1.0 400 Bad Request", null, true);
            return;
        }

        //Handle errors for HTTP command
        if( command.equals("DELETE") || command.equals("PUT") || command.equals("LINK") || command.equals("UNLINK") ){
            writeToClient("HTTP/1.0 501 Not Implemented", null, true);
            return;
        }else if( !command.equals("GET") && !command.equals("POST") && !command.equals("HEAD") ){
            writeToClient("HTTP/1.0 400 Bad Request", null, true);
            return;
        }

        //Handle errors for POST request
        long postContentLength = -1;
        String postContentType = null;
        HashMap<String, String> envMap = new HashMap<String, String>();;
        if(command.equals("POST")){
            try{
                for( int i = 1; i < request.size(); i++){
                    String postHeader = request.get(i);
                    if( postHeader.contains("Content-Length: ") && postHeader.substring(0, 16).equals("Content-Length: ") ){
                        postContentLength = Long.parseLong(postHeader.substring(16));
                        envMap.put("CONTENT_LENGTH", postHeader.substring(16));
                    }else if( postHeader.contains("Content-Type: ") && postHeader.substring(0, 14).equals("Content-Type: ") ){
                        postContentType = postHeader.substring(14);
                    }else if(postHeader.contains("From")){
                        String[] parse = postHeader.split(": ");
                        envMap.put("HTTP_FROM", parse[1]);
                    }else if(postHeader.contains("User-Agent:")){
                        String[] parse = postHeader.split(": ");
                        envMap.put("HTTP_USER_AGENT", parse[1]);
                    }
                }
                if( postContentLength == -1 ){
                    writeToClient("HTTP/1.0 411 Length Required", null, true);
                    return;
                }
                if( postContentType == null ){
                    writeToClient("HTTP/1.0 500 Internal Server Error", null, true);
                    return;
                }
                if( !resource.substring(resource.length()-4, resource.length()).equals(".cgi") ){
                    writeToClient("HTTP/1.0 405 Method Not Allowed", null, true);
                    return;
                }

            }catch(NumberFormatException e){
                writeToClient("HTTP/1.0 411 Length Required", null, true);
                return;
            }
        }


        try{
            //read resource
            File file = new File(resource.substring(1)); 
            String cookie = null;
            System.out.println("resource: " + resource.substring(1));
            if(resource.equals("/")){
                for(int i = 1; i < request.size(); i++){
                    if(request.get(i).contains("Cookie: ")){
                        cookie = request.get(i).replace("Cookie: lasttime=", "");
                        //check if cookie is before or on current day
                        if(!isCookieValid(cookie, getEncodedDateTime())){
                            System.out.println("Cookie is not valid");
                            cookie = null;
                        }
                        System.out.println(cookie);
                        break;
                    }
                }
                if(cookie!=null){
                    file = new File("index_seen.html");
                }else{
                    file = new File("index.html");
                }

            }if(!file.exists()){
                writeToClient("HTTP/1.0 404 Not Found", null, true);
                return;
            }
            if(!file.canRead() || file.isDirectory()){
                writeToClient("HTTP/1.0 403 Forbidden", null, true);
                return;
            }

            //if-last-modified-since
            boolean ifModifiedSince = false;
            if(request.size() == 2 && !command.equals("HEAD")){
                String dateStr = request.get(1).replace("If-Modified-Since: ", "");
                SimpleDateFormat formatter = new SimpleDateFormat("EEE, dd MMM YYYY HH:mm:ss zzz");
                formatter.setLenient(false);
                try{
                    Date dateSince = (Date) formatter.parse(dateStr);
                    Date dateLast = (Date) formatter.parse(formatDate(file.lastModified()));
                    if( dateLast.before(dateSince) ){
                        ifModifiedSince = true;
                    }
                }catch (ParseException e){
                    //do nothing
                }
            }

            //Create header
            String msg = (!ifModifiedSince)? "HTTP/1.0 200 OK\r\n" : "HTTP/1.0 304 Not Modified\r\n";
            msg += "Allow: " + "GET, POST, HEAD"+ "\r\n";
            msg += "Content-Encoding: " + "identity" + "\r\n";
            
            String contentType = Files.probeContentType(Paths.get(file.getName()));
            if(command.equals("POST")){
                contentType = "text/html";
            }
            if(contentType == null){
                contentType = "application/octet-stream";
            }
            msg += "Content-Type: " + contentType + "\r\n";

            msg += "Set-Cookie: lasttime=" + getEncodedDateTime() + "\r\n"; 

            msg += "Expires: " + "Thu, 01 Dec 2021 16:00:00 GMT"+ "\r\n";
            msg += "Last-Modified: " + formatDate(file.lastModified())+ "\r\n";
            //msg += "\r\n";

            //send message for POST
            if(command.equals("POST")){

                File program = new File(resource);
                //println("can execute: " + program.canExecute());
                

                //decode
                String arg1 = "."+resource; 
                int paramSepIndex = 0;
                while(!request.get(paramSepIndex).isBlank()){
                    paramSepIndex++;
                }
                paramSepIndex++;
                String param = "";
                while(paramSepIndex < request.size()){
                    param += request.get(paramSepIndex);
                    paramSepIndex++;
                }                
                //System.out.println("decode: " + param);
                String arg2 = decode(param);
                //println(arg1 + " " + arg2);

                //set environment vars
                //envMap.put("CONTENT_LENGTH", Integer.toString(arg2.getBytes().length));
                envMap.put("SCRIPT_NAME", resource);
                envMap.put("SERVER_NAME", HTTP3Server.serverIP);
                envMap.put("SERVER_PORT", HTTP3Server.serverPort);


                //process bulder
                try{
                    ProcessBuilder builder = new ProcessBuilder(arg1);
                    builder.environment().putAll(envMap);

                    Process process = builder.start();

                    OutputStream out = process.getOutputStream();
                    out.write(arg2.getBytes());
                    out.close();

                    BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String body = "";
                    int charInt;
                    while((charInt=in.read()) != -1){
                        body += (char) charInt;
                    } 

                    if(body.isEmpty()){
                        writeToClient("HTTP/1.0 204 No Content", null, true);;
                        return;
                    }

                    ArrayList<byte[]> bodyList = new ArrayList<byte[]>();
                    bodyList.add(body.getBytes());
                    in.close();
                    writeToClient(msg, bodyList, false);
                }catch(IOException e){
                    writeToClient("HTTP/1.0 403 Forbidden", null, true);
                    return;
                }

                return;
            }

            //send message for GET and HEAD
            if(!ifModifiedSince && !command.equals("HEAD")){   //Create body for GET

                //BufferedReader in = new BufferedReader(new FileInputStream(file));
                FileInputStream fis = new FileInputStream(file);
                String body = "";
                int charInt;
                while((charInt=fis.read()) != -1){
                    body += (char) charInt;
                } 

                if(cookie!=null){
                    if(body.contains("%YEAR-%MONTH-%DAY %HOUR-%MINUTE-%SECOND")){
                        System.out.println("str found");
                    }else{
                        System.out.println("str not found");
                    }
                    body = body.replace("%YEAR-%MONTH-%DAY %HOUR-%MINUTE-%SECOND", getDecodedDateTime(cookie));
                }
                msg+="Content-Length: " + body.length() + "\r\n\r\n";

                ArrayList<byte[]> bodyList = new ArrayList<byte[]>();
                bodyList.add(body.getBytes());
                fis.close();
                writeToClient(msg, bodyList, false);

                /*
                writeToClient(msg, body, false);
                fis.close();
                */
                return;

            }else{  //Write header only if HEAD or IF-MODIFIED-SINCE
                writeToClient(msg, null, false);
                return;
            }

        }catch(Exception e){
            //println("197 Error 500");
            e.printStackTrace();
            writeToClient("HTTP/1.0 500 Internal Server Error", null, true);
            return;
        }
    }

    

    public String getEncodedDateTime(){
        LocalDateTime myDateObj = LocalDateTime.now();
        DateTimeFormatter myFormatObj = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String formattedDate = myDateObj.format(myFormatObj);
        try{
            String encodedDateTime = URLEncoder.encode(formattedDate, "UTF-8");
            return encodedDateTime;

        }catch(UnsupportedEncodingException e){
            System.out.println("encoding error");   
            return null;
        }
    }

    public String getDecodedDateTime(String encoded){
        try{
            String decoded = URLDecoder.decode(encoded, "UTF-8");
            decoded = decoded.replace(":", "-");
            return decoded;
        }catch(UnsupportedEncodingException e){
            return null;
        }
    }

    //format date
    public String formatDate( long seconds ){
        Date date = new Date(seconds);
        SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM YYYY HH:mm:ss zzz");
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        return format.format(date);
    }



    //Create output stream and write <msg> to client, closes socket
    public void writeToClient(String msg, ArrayList<byte[]> body, boolean error){
        
        //println("\n# Response: " + msg);

        try{
            BufferedOutputStream outToClient = new BufferedOutputStream(clientSocket.getOutputStream());

            long bodylength = 0;
            if(body!=null){
                for(byte[]arr : body){
                    bodylength += arr.length;
                }
            }
            if( !error && !msg.contains("Content-Length")){
                msg += "Content-Length: " + bodylength + "\r\n\r\n";
            }

            outToClient.write(msg.getBytes());
            println(msg);
            
            if( body != null){
                println("=====StartBody, size("+ body.size()+")======");
            	for(int i = 0; i < body.size(); i++){
                    outToClient.write(body.get(i));
                    System.out.println(new String(body.get(i)));
                    /*
                    if(testcase == debug){
                       outToFile.write(body.get(i));
                    }
                    */
                   // String str = new String(body.get(i));
                    ////println(str);
                }
                println("==========END BODY (length:" + body.get(0).length+")========");
            }
            /*
            if(testcase == debug){
                outToFile.flush();
                outToFile.close();
            }
            */
            outToClient.flush();


            try{
                Thread.sleep(250);
            }catch(InterruptedException e){
            }

            outToClient.close();
            clientSocket.close();
            HTTP3Server.threadManager.removeClient(this);
            //return
        }catch(IOException e){
            //e.printStackTrace();
        }
    }

    public boolean isCookieValid(String encodedCookie, String encodedCurrentTime){
        try{
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String decodedCookie = URLDecoder.decode(encodedCookie, "UTF-8");
            String decodedCurrentTime = URLDecoder.decode(encodedCurrentTime, "UTF-8");
            System.out.println("Cookie:  " + decodedCookie);
            System.out.println("Current: " + decodedCurrentTime);

            boolean isValid = format.parse(decodedCurrentTime).after(format.parse(decodedCookie));
            System.out.println("isValid: " + isValid);
            return isValid;
        }catch(UnsupportedEncodingException e){
            System.out.println("e1");
            return false;
        }catch(ParseException e){
            System.out.println("e2");
            return false;
        }
    }

	public static String decode(String str) {
		String reserved = "!*'();:@$+,/?#[]";
		int index = -1;
		do {
			index = str.indexOf("!", index+1);
			if( index != -1 
					&& index!=str.length()-1 
					&& reserved.contains(Character.toString(str.charAt(index+1)))) {
			
				str = str.substring(0, index) + str.substring(index+1);
			}
		}while(index != -1);
		return str;
	}

    public void println(String s){
       // if(testcase == debug){
            System.out.println(s);
        //}
    }

    public void println(){
        //if(testcase == debug){
            System.out.println();
        //}
    }

    public void print(String s){
       // if(testcase == debug){
            System.out.print(s);
        //}
    }
}