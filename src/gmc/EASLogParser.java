package gmc;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class EASLogParser {
    static final String ENTRY = "entry";
    static final String COMMAND = "command";
    static final String REQUESTTIME = "requesttime";
    static final String RESPONSETIME = "responsetime";
    static final String NEXT_IS_REQUESTTIME = "start";
    static final String NEXT_IS_RESPONSETIME = "end";
    static final String PROCESSING_REQUEST_BODY = "request";
    static final String PROCESSING_RESPONSE_BODY = "response";
    static final String NO_XMLRESPONSE = "no xml response";
    static final String SERVER_EXCEPTION_ENCOUNTERED = "server exception";
    static final String NO_RESPONSE = "no response";
    static final String HEADERS = "headers";
    static final String PENDING_RESPONSE = "pending response";
    
    File file_;
    int lines_;
    List<EASLogEntry>entries_;
    StringBuffer requestXML_;
    StringBuffer responseXML_;
    StringBuffer mimeBody_;
    Map<String,String>headers_;
        
//    long bytesTransferred_;
    
    EASLogParser(String filename) throws FileNotFoundException{
        file_ = new File(filename);
        if(!file_.exists())
            throw new FileNotFoundException(filename);
        entries_ = new ArrayList<EASLogEntry>();
    }
    
    EASLogData parse()throws IOException{
        InputStream fis;
        BufferedReader br = null;
        String line;
        Alarms alarms = new Alarms();

        Map<String, String>ctx = new HashMap<String, String>();
        requestXML_ = new StringBuffer();
        responseXML_ = new StringBuffer();
        mimeBody_ = null;
        headers_ = new HashMap<String,String>();

        try{
            fis = new FileInputStream(file_);
            br = new BufferedReader(new InputStreamReader(fis, Charset.forName("UTF-8")));
             
            while ((line = br.readLine()) != null) {
                if(handleLine(ctx, line, alarms)){
                    String entryNum = ctx.get(ENTRY);
                    String startTime = ctx.get(REQUESTTIME);
                    String endTime = ctx.get(RESPONSETIME);
                    String cmd = ctx.get(COMMAND);
                    boolean serverException = ctx.get(SERVER_EXCEPTION_ENCOUNTERED) != null;
                    boolean pendingResponse = ctx.get(PENDING_RESPONSE) != null;
                    boolean noResponse = ctx.get(NO_RESPONSE) != null;
                    entries_.add(EASLogEntry.create(entryNum, startTime, endTime, cmd, requestXML_.toString(), 
                            noResponse ? null : responseXML_.toString(), serverException, pendingResponse, headers_, alarms));
                    ctx.clear();
                    requestXML_ = new StringBuffer();
                    responseXML_ = new StringBuffer();
                    mimeBody_ = null;
                    headers_ = new HashMap<String,String>();
                }
            }
            
            // and the last one too
            if(ctx.get(ENTRY) != null){
                String entryNum = ctx.get(ENTRY);
                String startTime = ctx.get(REQUESTTIME);
                String endTime = ctx.get(RESPONSETIME);
                String cmd = ctx.get(COMMAND);
                boolean serverException = ctx.get(SERVER_EXCEPTION_ENCOUNTERED) != null;
                boolean pendingResponse = ctx.get(PENDING_RESPONSE) != null;
                entries_.add(EASLogEntry.create(entryNum, startTime, endTime, cmd, requestXML_.toString(),
                        responseXML_.toString(), serverException, pendingResponse, headers_, alarms));
            }
            return EASLogData.create(file_.getAbsolutePath(), entries_, alarms);
        }finally{
            try {
                if(br != null)
                  br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            br = null;
            fis = null;
        }
    }
    
    boolean handleLine(Map<String, String> ctx, String lineFromLog, Alarms alarms){
        boolean gotLogEntry = false;
        lines_++;
        if(lineFromLog.length() == 0)
            return gotLogEntry;
        
        if(lineFromLog.startsWith(" Log Entry: ")){
            ctx.put(ENTRY, lineFromLog.substring(" Log Entry: ".length()));
        }else if(lineFromLog.equals("RequestTime : ")){
            ctx.put(NEXT_IS_REQUESTTIME, Boolean.TRUE.toString());
        }else if(ctx.get(NEXT_IS_REQUESTTIME) != null){
            ctx.put(NEXT_IS_REQUESTTIME, null);
            ctx.put(REQUESTTIME, lineFromLog);
        }else if(lineFromLog.startsWith("POST /Microsoft-Server-ActiveSync")){
            int cmdIdx = lineFromLog.indexOf("Cmd=") + "Cmd=".length();
            String cmd = lineFromLog.substring(cmdIdx);
            cmdIdx = cmd.indexOf('&');    // handle the case where the cmd is last (and there is no trailing " HTTP/1.1"
            if(cmdIdx == -1) cmdIdx = cmd.length();
            cmd = cmd.substring(0, cmdIdx);
            cmdIdx = cmd.indexOf(' ');    // handle the case where the cmd is last (and there is a trailing " HTTP/1.1"
            if(cmdIdx > 0) cmd = cmd.substring(0, cmdIdx);
            
            ctx.put(COMMAND, cmd);
            
            extractQueryArg(lineFromLog, "User");
            extractQueryArg(lineFromLog, "DeviceID");
            extractQueryArg(lineFromLog, "DeviceType");

            ctx.put(HEADERS, Boolean.TRUE.toString());
        }else if(lineFromLog.equals("RequestBody : ")){
            ctx.put(PROCESSING_REQUEST_BODY, Boolean.TRUE.toString());
            ctx.put(HEADERS, null);
        }else if(lineFromLog.equals("LogicalRequest : ") || lineFromLog.equals("AccessState : ")){
            ctx.put(PROCESSING_REQUEST_BODY, null);
            ctx.put(PROCESSING_RESPONSE_BODY, null);  // multipart
        }else if(lineFromLog.equals("WasPending : ")){
            //System.err.println(String.format("Pending response detected at line %d", lines_));
            ctx.put(PROCESSING_REQUEST_BODY, null);
            ctx.put(PENDING_RESPONSE, lineFromLog); 
        }else if(lineFromLog.equals("SyncCommand_GenerateResponsesXmlNode_AddChange_ConvertServerToClientObject_Exception : ")){  // TODO: flag this !
            //System.err.println(String.format("Server error generating response at line %d", lines_));
            ctx.put(PROCESSING_REQUEST_BODY, null); 
            ctx.put(SERVER_EXCEPTION_ENCOUNTERED, lineFromLog); 
        }else if(lineFromLog.equals("SyncCommand_OnExecute_Exception : ")){   // TODO: flag this !
            //System.err.println(String.format("Server error generating response at line %d", lines_));
            ctx.put(PROCESSING_REQUEST_BODY, null); 
            ctx.put(SERVER_EXCEPTION_ENCOUNTERED, lineFromLog); 
        }else if(lineFromLog.equals("ResponseBody : ")){
            ctx.put(PROCESSING_REQUEST_BODY, null);
            ctx.put(PROCESSING_RESPONSE_BODY, Boolean.TRUE.toString());
        }else if(lineFromLog.equals("ResponseTime : ")){
            ctx.put(PROCESSING_RESPONSE_BODY, null);
            ctx.put(NEXT_IS_RESPONSETIME, Boolean.TRUE.toString());
        }else if(ctx.get(NEXT_IS_RESPONSETIME) != null){
            ctx.put(NEXT_IS_RESPONSETIME, null);
            ctx.put(RESPONSETIME, lineFromLog);
            gotLogEntry = true;
        }else if(ctx.get(HEADERS) != null){
            extractHeader(lineFromLog);   
        }else{
            
            // not a state change, capture the xml (if applicable)
            boolean doingRequest = ctx.get(PROCESSING_REQUEST_BODY) != null;
            boolean doingResponse = ctx.get(PROCESSING_RESPONSE_BODY) != null;
            
            // The XML that server reports is not valid in the case where it is reporting the byte size of a request. Eg, when a 
            // ItemsOperation Fetch reports the body the generated XML is:
            //        <Body=1149 bytes/>
            // In contrast, there are other places where number of bytes are represented in the XML as an attribute, such as:
            //        <OrganizerEmail xmlns="Calendar:" bytes="13"/>
            // In addition to the body being invalid XML (so it won't parse) we also have code that looks for the embedded size 
            // attribute.
            // So we're gonna adjust the XML in the log to align with what will work out for us better as we continue the 
            // processing:
            //        <Body bytes="1149"/>
            String fixedLine = lineFromLog;
            int fixIdx = lineFromLog.indexOf("<Body=");
            if(fixIdx > 0){
                int endIdx = lineFromLog.indexOf(" bytes/>");
                String leadingWhitespace = lineFromLog.substring(0, fixIdx);
                String number = lineFromLog.substring(fixIdx+"<Body=".length(), endIdx);
                fixedLine = String.format("%s<Body bytes=\"%s\"/>", leadingWhitespace, number);
            }

            // TODO: handle multipart response
            // The multipart header confuses us too. Neuter it for now...
            // ----------------------- Multipart Response ---------------------
            // Number of Parts: 2
            // Part 0: offset 20, size 65
            // Part 1: offset 85, size 5495
            if(lineFromLog.startsWith("----------------------- Multipart Response")||
                    lineFromLog.startsWith("Number of Parts: 2")||
                    lineFromLog.startsWith("Part 0: offset ")||
                    lineFromLog.startsWith("Part 1: offset "))
                fixedLine = "";

            // "No XMLResponse" means no response...
            if(lineFromLog.equals("[No XmlResponse]")){
                ctx.put(NO_XMLRESPONSE, Boolean.TRUE.toString());
                fixedLine = "";
            }
            
            // There exist log entries where there is no response from the server (it is just a request). Eg:
            // RequestBody : 
            // <?xml version="1.0" encoding="utf-8" ?>
            // <Sync xmlns="AirSync:">
            //  <Collections>
            //      <Collection>
            //          <SyncKey>235327689</SyncKey>
            //          <CollectionId>7</CollectionId>
            //      </Collection>
            //      <Collection>
            //          <SyncKey>1234550746</SyncKey>
            //          <CollectionId>2</CollectionId>
            //      </Collection>
            //      <Collection>
            //          <SyncKey>1619129741</SyncKey>
            //          <CollectionId>15</CollectionId>
            //      </Collection>
            //      <Collection>
            //          <SyncKey>2007729570</SyncKey>
            //          <CollectionId>1</CollectionId>
            //      </Collection>
            //  </Collections>
            //  <Wait>1</Wait>
            //   <WindowSize>20</WindowSize>
            // </Sync>
            // 
            // -----------------
            // Log Entry: 30
            // -----------------
            // 
            // We'll need to defend the parser against this case. 
            if(doingRequest && lineFromLog.equals("-----------------")){
                // System.err.println(String.format("Request with no response at line %d", lines_));
                ctx.put(PROCESSING_RESPONSE_BODY, null);
                ctx.put(NO_RESPONSE, Boolean.TRUE.toString()); 
                gotLogEntry = true;
                fixedLine = "";
            }

            // Handle mime bodies, they span multiple lines
            if(doingRequest){
                if((mimeBody_ == null) && (lineFromLog.startsWith("\t<Mime>To: "))){
                    mimeBody_ = new StringBuffer(lineFromLog);
                    fixedLine = "";   // we'll collect it first and add it all at once
                }else if(mimeBody_ != null){
                    mimeBody_.append(lineFromLog);
                    fixedLine = "";  // keep collecting
                    if(lineFromLog.endsWith("</Mime>")){                        
                        fixedLine = mimeBody_.toString();
                        mimeBody_ = null;   // done collecting
                        
                        // turn it into xml with the special bytes entry describing the length
                        String to = fixedLine.substring(fixedLine.indexOf("<Mime>To: ") + "<Mime>To: ".length(), fixedLine.indexOf("Subject:"));
                        String subject = fixedLine.substring(fixedLine.indexOf("Subject:") + "Subject:".length(), fixedLine.indexOf("MIME-Version"));;
                        fixedLine = String.format("<mime bytes=\"%d\"><to></to><subject></subject></mime>", fixedLine.length(), to, subject);
                    }
                }
            }
            
            if(!fixedLine.isEmpty()){
                if(doingRequest){
                    requestXML_.append(fixedLine);
                    requestXML_.append("\n");                
                }
                if(doingResponse){
                    responseXML_.append(fixedLine);
                    responseXML_.append("\n");
                }
            }
        }
        return gotLogEntry;
    }
    
    private void extractQueryArg(String requestURL, String queryArg){
        int startIdx = requestURL.indexOf(queryArg);
        String tail = requestURL.substring(startIdx+queryArg.length() + 1);  // + 1 for the '='
        int endIdx = tail.indexOf('&');
        if(endIdx == -1)
            endIdx = tail.indexOf(' ');
        if(endIdx == -1)
            endIdx = tail.length()-1;
        String valu = tail.substring(0, endIdx);
        headers_.put(queryArg, valu);
    }
    
    private void extractHeader(String headerLine){
        String[] kv = headerLine.split(":");
        String k = kv[0].trim();
        
        if(k.equals("Content-Length"))
            return; // skip this (we don't want to this value to trigger a header mismatch)
        
        String v = kv[1].trim();
        headers_.put(k, v);
    }

}
