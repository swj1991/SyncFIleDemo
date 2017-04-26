package FileSync;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;

import filesync.BlockUnavailableException;
import filesync.Instruction;
import filesync.InstructionFactory;
import filesync.SynchronisedFile;

public class syncserver {
	 private static Socket connection;
	 private static ServerSocket server;
	 private static DataOutputStream output;
	 private static DataInputStream input;
	 private static SynchronisedFile tofile;
	 private static int size =1024;
	 private static String path = "";
	 private static int port = 4444;
   private syncserver(String path, int port) throws IOException{
	      this.path = path;
	      this.port=port;
	      server = new ServerSocket(this.port);
   }
   private static void waitForConnection() throws IOException{
	  connection = server.accept();
   }
   private static void setFile(String filename) throws IOException{
	   if(path.contains("\\")){
		   String thepath= path+"\\"+filename;
		   tofile = new SynchronisedFile(thepath,1024);
	   }else{
		   String thepath= path+"/"+filename;
		   tofile = new SynchronisedFile(thepath,1024);
	   }
   }
   
   private static void getStreams() throws IOException{
	   output = new DataOutputStream(connection.getOutputStream());
	   output.flush();
	   input = new DataInputStream(connection.getInputStream());
   }
   private static void closeConnection() throws IOException{
	   output.close();
       input.close();
       connection.close();
   }
   private static void sendData(String data) throws IOException{
	    output.writeUTF(data);
	    output.flush();
   }
   private static String[] getJsonArray(String hash_code) throws IOException{
	
		   String[] hasharray = hash_code.split("/");
		   return hasharray;
	         
   }
   private static boolean deleteFile(String filename){
	           File f = new File(filename);
	          if(f.delete()){
	        	  return true;
	          }else{
	        	  return false;
	          }
   }
   private static boolean createFile(String filename) throws IOException{
	          File f = new File(filename);
	          if(f.createNewFile()){
	        	    return true;
	          }else{
	        	    return false;
	          }
   }
	 private static String[] getFiles(String p){
		 File folder = new File(p);
		 File[] listOfFiles = folder.listFiles();
		 String[] files = new String[listOfFiles.length];
		            for(int i=0;i<listOfFiles.length;i++){
		            	files[i] = listOfFiles[i].getName();
		            }
		          return files;
	 }
   public static void main(String args[]) throws IOException{
	          String path= "toFile";
	          int port = 4444;
	          syncserver s = new syncserver(path,port);
	          System.out.println("waiting for connection...");
	          while(true){
	        	      waitForConnection();	        	  	  
	        		  getStreams();	  
	        		  sendData("connect successfully! you can create, delete and modify files now!");
		        	  while(true){
		        		  try{
		        			  String msg = input.readUTF();
		        			  if(msg.contains("MODIFY")){
		        				  String[] message = msg.split("\\\\");
		        				  InstructionFactory instFact=new InstructionFactory();		        				  
		        				  setFile(message[message.length-1]);	
		        				  boolean update = false;
		        				  ArrayList<String> block = new ArrayList<String>(); 
				            	     while(true){
				            		  String theblock = input.readUTF();
				            		  block.add(theblock);
				            		  Instruction receivedInst = instFact.FromJSON(theblock);
				            		  try {
										tofile.ProcessInstruction(receivedInst);
										if(theblock.contains("EndUpdate")){
											if(update==true){
											sendData("Update successfully!");
											}else if(block.size()==2){
										    sendData("Empty file!");
											}else{
										    sendData("nothing change!");	
											}
												 break;
										}else{
											sendData("keep sending");
										}
										     
									} catch (BlockUnavailableException e) {
										// TODO Auto-generated catch block
										      sendData("upgrade block");
										      String upgradeblock = input.readUTF();
										      update=true;
										      Instruction receivedInst2 = instFact.FromJSON(upgradeblock);
											  try {
												tofile.ProcessInstruction(receivedInst2);
											} catch (IOException e1) {
												e1.printStackTrace();
												System.exit(-1);
											} catch (BlockUnavailableException e1) {
												assert(false); // a NewBlockInstruction can never throw this exception
											}
									}
				            	    }
				            	   }else if(msg.contains("DELETE")){
				            		   boolean deleteornot = false;
				            		   while(deleteornot==false){
				            			   String filepath="";
					            		   String[] file = msg.split("\\\\");	
					            		   if(path.contains("\\")){
					            			   filepath=path+"\\"+file[file.length-1]; 
					            		   }else{
					            			   filepath=path+"/"+file[file.length-1]; 
					            		   }					            	
					            		   if(deleteFile(filepath)){
					            			   deleteornot=true;
					            			   sendData("Delete the file Successfully!");
					            		   }else{
					            			   sendData("Fail to delete!");
					            		   }					            		  
				            		   }			            		   
				            	   }else if(msg.contains("CREATE")){
				            		   boolean createornot = false;
				            		   while(createornot==false){
				            			   String filepath="";
					            		   String[] file = msg.split("\\\\");	
					            		   if(path.contains("\\")){
					            			   filepath=path+"\\"+file[file.length-1]; 
					            		   }else{
					            			   filepath=path+"/"+file[file.length-1]; 
					            		   }
					            		   if(createFile(filepath)){
					            			   createornot=true;
					            			   sendData("Create a file Successfully!");
					            		   }else{
					            			   sendData("Fail to create a file!");
					            		   }
				            		   }
				            	
				            	   }else if(msg.contains("CHECK")){
				            		     String exist = "false";
				            		     String[] hash = msg.split("\\\\");
				            		     String filename = hash[hash.length-1];
				            		     
				            		     String[] files = getFiles(path);
				            		     //System.out.println(files.length);
				            		     if(files.length>0){
				            		    	 for(int i=0;i<files.length;i++){
				            		    		 if(files[i].equals(filename)){
				            		    			 exist="true";
				            		    		 }
				            		    	 }
				            		     }		
				            		     sendData(exist);
				            	   }
		        		           }catch(SocketException e){		        		        	   
		        			               break;		        			               
		        		           }		             		                 
		        	  }
		        	
		              }        	              
	          }
  }

