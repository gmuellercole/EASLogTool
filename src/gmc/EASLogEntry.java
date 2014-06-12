package gmc;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

class EASLogEntry {
    final int entryNum_;
    final String startTimeStr_;
    final String endTimeStr_;
    final Date startDate_;
    final Date endDate_;
    final int durationSecs_;
    final String cmd_;
    final long requestSize_;
    final long responseSize_;
    final long bytesTransferred_;
    final boolean serverException_;
    final boolean pendingResponse_;
    final Map<String, String>headers_;
    EntryData data_;
    int alarms_;
    String details_;
        
    private static abstract class EntryData{
        abstract String dump();
        int collectAlarms(){
            return Alarms.NoAlarm;
        }
    };
    private static abstract class CollectionsData extends EntryData{
        private static final int ADDS = 0;
        private static final int REMOVES = 1;
        private static final int CHANGES = 2;

        List<String>collections_;
        Map<String, ArrayList<String>[]>collectionsMap_;
        
        CollectionsData(NodeList collectionsElementList){
            collections_ = new ArrayList<String>();
            collectionsMap_ = new HashMap<String, ArrayList<String>[]>();
            
            for(int i = 0; i < collectionsElementList.getLength(); i++){
                Node node = collectionsElementList.item(i);
                if(node.getNodeType() == Node.ELEMENT_NODE){
                    Element collectionsElement = (Element)node;
                    NodeList collections = collectionsElement.getChildNodes();
                    for(int j = 0; j < collections.getLength(); j++){
                        Node collectionNode = collections.item(j);
                        if(collectionNode.getNodeType() == Node.ELEMENT_NODE){
                            Element collectionElement = (Element)collectionNode;
                            if(collectionNode.getNodeType() == Node.ELEMENT_NODE){
                                handleCollectionRequest(collectionElement);
                            }
                        }
                    }
                    break; // there is only one Collections element...
                }
            }
        }
    
        void handleCollectionRequest(Element collectionElement){
            Node collectionIdElement = collectionElement.getElementsByTagName("CollectionId").item(0);
            String collectionId = collectionIdElement.getTextContent();
            ArrayList<String>[] addsRemovesChanges = new ArrayList[3];
            addsRemovesChanges[ADDS] = new ArrayList<String>();
            addsRemovesChanges[REMOVES] = new ArrayList<String>();
            addsRemovesChanges[CHANGES] = new ArrayList<String>();
            collections_.add(collectionId);
            collectionsMap_.put(collectionId, addsRemovesChanges);
            handleCollectionItem(addsRemovesChanges[ADDS], collectionElement.getElementsByTagName("Add"));
            handleCollectionItem(addsRemovesChanges[REMOVES], collectionElement.getElementsByTagName("Delete"));
            handleCollectionItem(addsRemovesChanges[CHANGES], collectionElement.getElementsByTagName("Change"));
        }

        void handleResponse(NodeList collectionsElementList){
            for(int i = 0; i < collectionsElementList.getLength(); i++){
                Node node = collectionsElementList.item(i);
                if(node.getNodeType() == Node.ELEMENT_NODE){
                    Element collectionsElement = (Element)node;
                    NodeList collections = collectionsElement.getChildNodes();
                    for(int j = 0; j < collections.getLength(); j++){
                        Node collectionNode = collections.item(j);
                        if(collectionNode.getNodeType() == Node.ELEMENT_NODE){
                            Element collectionElement = (Element)collectionNode;
                            if(collectionNode.getNodeType() == Node.ELEMENT_NODE){
                                handleCollectionResponse(collectionElement);
                            }
                        }
                    }
                    break; // there is only one Collections element...
                }
            }
        }
        
        void handleCollectionResponse(Element collectionElement){
            Node collectionIdElement = collectionElement.getElementsByTagName("CollectionId").item(0);
            
            String collectionId = collectionIdElement.getTextContent();
            ArrayList<String>[] addsRemovesChanges = collectionsMap_.get(collectionId);
            handleCollectionItem(addsRemovesChanges[ADDS], collectionElement.getElementsByTagName("Add"));
            handleCollectionItem(addsRemovesChanges[REMOVES], collectionElement.getElementsByTagName("Delete"));
            handleCollectionItem(addsRemovesChanges[CHANGES], collectionElement.getElementsByTagName("Change"));
        }
        
        void handleCollectionItem(ArrayList<String>holder, NodeList items){
            for(int i = 0; i < items.getLength(); i++){
                Node node = items.item(i);
                if(node.getNodeType() == Node.ELEMENT_NODE){
                    String serverId = node.getTextContent(); 
                    holder.add(serverId);
                }
            }            
        }
    };
    
    private static class SyncEntryData extends CollectionsData{
        Map<String, String> filterTypeMap_;
        Map<String, String> statusMap_;
        Map<String, Boolean> moreMap_;
        Map<String, String> responseSyncKeyMap_;
        
        SyncEntryData(Element syncElement){
            super(syncElement.getElementsByTagName("Collections"));
            statusMap_ = new HashMap<String, String>();
            moreMap_ = new HashMap<String, Boolean>();
            responseSyncKeyMap_ = new HashMap<String, String>();
        }
        
        void handleCollectionResponse(Element collectionElement){
            super.handleCollectionResponse(collectionElement);
            Node collectionIdElement = collectionElement.getElementsByTagName("CollectionId").item(0);
            String collectionId = collectionIdElement.getTextContent();
            
            Node statusElement = collectionElement.getElementsByTagName("Status").item(0);
            String status = statusElement.getTextContent();
            
            Node moreElement = collectionElement.getElementsByTagName("MoreAvailable").item(0);
            Boolean more = new Boolean(moreElement != null);

            Node syncKeyElement = collectionElement.getElementsByTagName("SyncKey").item(0);
            String syncKey = syncKeyElement.getTextContent(); 

            statusMap_.put(collectionId,  status);
            moreMap_.put(collectionId, more);
            responseSyncKeyMap_.put(collectionId, syncKey);
        }

        void handleResponse(Element syncElement){
            super.handleResponse(syncElement.getElementsByTagName("Collections"));
        }
        
        void handleCollectionRequest(Element collectionElement){
            super.handleCollectionRequest(collectionElement);
            Node collectionIdElement = collectionElement.getElementsByTagName("CollectionId").item(0);
            String collectionId = collectionIdElement.getTextContent();

            Element optionsElement = (Element)collectionElement.getElementsByTagName("Options").item(0);
            String filterType = "?";
            if(optionsElement != null){
                Node filterTypeElement = optionsElement.getElementsByTagName("FilterType").item(0);
                if(filterTypeElement != null){
                    filterType = filterTypeElement.getTextContent();
                }
            }
            
            if(filterTypeMap_ == null){
                filterTypeMap_ = new HashMap<String, String>();
            }
            filterTypeMap_.put(collectionId, filterType);
        }
        
        String dump(){
            return String.format("%s (%s)", getSummary(), getDetails());
        }
        
        int countEntries(int idx){
            int rv = 0;
            for (String collectionId : collections_) {
                ArrayList<String>[]acr = collectionsMap_.get(collectionId);
                rv += acr[idx].size();
            }
            return rv;
        }

        String getSummary(){
            String summaryMsg = String.format("[%d collections : +%d -%d ~%d]", collections_.size(), countEntries(CollectionsData.ADDS), 
                    countEntries(CollectionsData.REMOVES), countEntries(CollectionsData.CHANGES));
            return summaryMsg;
        }
        
        String getDetails(){
            StringBuffer detailsBuffer = new StringBuffer();
            for (String collectionId : collections_) {
                ArrayList<String>[]acr = collectionsMap_.get(collectionId);
                String filtertype = filterTypeMap_.get(collectionId);
                String status = statusMap_.get(collectionId);
                status = (status == null) ? "" : String.format(", status=%s", status);
                Boolean moreAvailable = moreMap_.get(collectionId); 
                String more =  (moreAvailable != null) && (moreAvailable.booleanValue()) ? ", more=yes" : "";
                detailsBuffer.append(String.format("[id=%s, ftype=%s : +%d -%d ~%d%s%s]", collectionId, filtertype, acr[CollectionsData.ADDS].size(), 
                        acr[CollectionsData.REMOVES].size(), acr[CollectionsData.CHANGES].size(), status, more));
            }
            String details = detailsBuffer.toString();
            return details;
        }

        @Override
        int collectAlarms(){
            int syncSpecificAlarms = Alarms.NoAlarm; 
            for (String collectionId : collections_) {
                String status = statusMap_.get(collectionId);
                if(status != null && !status.equals("1")){
                    syncSpecificAlarms |= Alarms.NotSuccessAlarm;
                    break;
                }
            }

            return super.collectAlarms() + syncSpecificAlarms;
        }

    };
    
    static DocumentBuilderFactory docFactory_ = DocumentBuilderFactory.newInstance();
    
    static EASLogEntry create(String entryNum, String startTime, String endTime, String cmd, String requestXML, String responseXML, 
            boolean serverException, boolean pendingResponse, Map<String, String>headers, Alarms alarms){
        return new EASLogEntry(entryNum, startTime, endTime, cmd, requestXML, responseXML, serverException, 
                pendingResponse, headers, alarms);
    }
    
    private EASLogEntry(String entryNum, String startTime, String endTime, String cmd, String requestXML, String responseXML, 
            boolean serverException, boolean pendingResponse, Map<String, String>headers, Alarms alarms){
        // initialize the alarm signal, value can be updated during subsequent processing
        alarms_ = Alarms.NoAlarm;

        //System.out.println("Creating entry:" + entryNum);
        entryNum_ = Integer.valueOf(entryNum);
        //if(entryNum_ == 131)
        //    System.out.println("bang");
        startTimeStr_ = startTime;
        endTimeStr_ = endTime;        
        startDate_ = startTimeStr_ == null ? null : getDate(startTimeStr_);
        endDate_ = endTimeStr_ == null ? null : getDate(endTimeStr_);
        durationSecs_ = (endDate_ == null || startDate_ == null) ? 0 : ((int)(endDate_.getTime() - startDate_.getTime())) / 1000;
        cmd_ = cmd;
        details_ = "";
        serverException_ = serverException;
        pendingResponse_ = pendingResponse;
        headers_ = headers;

        if(startTimeStr_ == null){
            alarms_ = alarms.add(alarms_, Alarms.NoStartTimeAlarm);
        }
        if(serverException_){
            alarms_ = alarms.add(alarms_, Alarms.ServerExceptionAlarm);
        }
        if(pendingResponse_){
            alarms_ = alarms.add(alarms_, Alarms.PendingResponseAlarm);
        }
        
        requestSize_ = handleXML(requestXML, true, alarms);
        long sizeofResponse = 0;
        if(responseXML == null){
            alarms_ = alarms.add(alarms_, Alarms.NoServerResponseAlarm);
        }else if(responseXML.isEmpty()){
            // empty response is what Sync and Ping normally get
        }else{
            sizeofResponse = handleXML(responseXML, false, alarms);
        }
        responseSize_ = sizeofResponse;
        bytesTransferred_ = requestSize_ + responseSize_;
    }
    
    void process(EASLogEntry priorEntry, Alarms alarms){
        // add entry specific alarms
        if(data_ != null){
            alarms_ = alarms.add(alarms_, data_.collectAlarms());
        }
        
        if(priorEntry != null){
            // check timing
            if(priorEntry.endDate_ != null && startDate_ != null && startDate_.before(priorEntry.endDate_)){
                alarms_ = alarms.add(alarms_, Alarms.ConcurrentEntriesAlarm);
            }
            
            // check headers
            if(headers_.size() != priorEntry.headers_.size()){
                //System.err.println(String.format("Header size mismatch. Prior had %d, current has %d headers", priorEntry.headers_.size(), headers_.size()));
                alarms_ = alarms.add(alarms_, Alarms.HeadersChangedAlarm);
            }else{
                for(String k : headers_.keySet()){
                    String v1 = headers_.get(k);
                    String v2 = priorEntry.headers_.get(k);
                    if(v1 == null && v2 == null){
                        // odd, but still a match  keep looking
                    }else if(v1 == null || v2 == null){
                        //System.err.println("got null headers");
                        alarms_ = alarms.add(alarms_, Alarms.HeadersChangedAlarm);
                        break;
                    }else if(!v1.equals(v2)){
                        //System.err.println(String.format("Header mismatch for '%s'. Prior:%s, Current:%s", k, v2, v1));
                        alarms_ = alarms.add(alarms_, Alarms.HeadersChangedAlarm);
                        break;
                    }                
                }
            }
        }
    }
    
    private Date getDate(String s){
        SimpleDateFormat inputFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
        try {
            Calendar cal = Calendar.getInstance();
            cal.setTime(inputFormat.parse(s));
            return cal.getTime();
        } catch (ParseException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }
    
    private long handleXML(String xml, boolean isRequest, Alarms alarms){
//        System.out.println("Handling XML:");
//        System.out.println(xml);
        
        DocumentBuilder builder;
        long rv = 0l;
        try {
            builder = docFactory_.newDocumentBuilder();
            
            InputStream is = new ByteArrayInputStream( xml.getBytes());
            Document document = builder.parse(is);
            rv = determineApproximateSize(document.getDocumentElement());
            is.close();
            String type = document.getDocumentElement().getTagName();
            if(type.equals("Sync")){
//                alarms_ = alarms.remove(alarms_, Alarms.NoServerResponseAlarm);
                collectSyncData(document, isRequest);
            }
        } catch (ParserConfigurationException | SAXException | IOException e) {
            if(e instanceof SAXException){
                System.out.println("Error at entry " + entryNum_);
                System.out.println(xml);
            }
            e.printStackTrace();
        }

        return rv;
    }
    
    long determineApproximateSize(Element e){
        long size = 0; // one for the open tag, one for the close tag
        size = 2; // one byte for open tag, one byte for the close tag
        NamedNodeMap nodeMap = e.getAttributes();
        if(nodeMap.getNamedItem("xmlns") != null){
            size++;  // add the codepage switch
        }
        Node bytesSizeNode = nodeMap.getNamedItem("bytes");
        if(bytesSizeNode != null){
            String byteSizeStr = bytesSizeNode.getNodeValue();
            size += Long.parseLong(byteSizeStr);  // add the number of bytes that AS says are present
        }
        String value = e.getTextContent();
        if(value != null){
            size += value.length();   // account for the actual value being sent
        }
        
        // recurse through the node
        NodeList children = e.getChildNodes();
        for(int i = 0; i < children.getLength(); i++){
            Node thisChild = children.item(i);
            if(thisChild.getNodeType() == Node.ELEMENT_NODE){
                size += determineApproximateSize((Element)thisChild);    
            }
        }
        
        return size;
    }
    
    void collectSyncData(Document document, boolean isRequest){
        if(isRequest){
            Element syncElement = document.getDocumentElement();
            data_ = new SyncEntryData(syncElement);
        }else{  // it is a response
            SyncEntryData syncData = (SyncEntryData)data_;
            Element syncElement = document.getDocumentElement();
            syncData.handleResponse(syncElement);
            details_ = syncData.dump();
        }
    }
    
    String getDetails(){
        return details_;
    }
}
