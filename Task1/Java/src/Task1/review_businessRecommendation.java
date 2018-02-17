//---------------------------------------------------------------------------
// Filename: review_businessRecommendation.java
// Objective: To implement the content based recommendation system
// INPUT: business.JSON, User.JSON, review.JSON
// OUTPUT: buzReco.CSV and model statistics
//---------------------------------------------------------------------------

package Task1;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.analysis.standard.ClassicAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BooleanQuery.TooManyClauses;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.json.JSONObject;


public class review_businessRecommendation {

	public HashSet<String> trainUserId = new HashSet<String>();
	public HashSet<String> testUserId = new HashSet<String>();
	public HashSet<String> citybusinessId = new HashSet<String>();
	public HashMap<String, String> businessId_name = new HashMap<String, String>();
	public HashMap<String, String> userId_name = new HashMap<String, String>();

	private BufferedReader rev, buz, usr;
	public static void main(String[] args) throws FileNotFoundException, IOException, ParseException, org.apache.lucene.queryparser.classic.ParseException {

		FileWriter writer = new FileWriter("buzReco.csv",false);

		review_businessRecommendation md = new review_businessRecommendation();
		String path = "/Users/shreyas/Downloads/dataset/";
		String city = "Belmont";
		String state = "NC";		
		int counter = 1;

		Analyzer [] analyzers = {new StandardAnalyzer(), new ClassicAnalyzer()};
		Similarity [] similarities = {new ClassicSimilarity(), new BM25Similarity(), new LMDirichletSimilarity(), new LMJelinekMercerSimilarity((float) 0.7)};

		for(Analyzer analyzer:analyzers) {
			for(Similarity similarity:similarities) {
				System.out.println(counter + "/" + analyzers.length * similarities.length);
				md.fetchUsers(path, city, state);

				md.generateIndex_trainUsers(path
						,"/Users/shreyas/Downloads/index/"
						,analyzer);

				String returnFile = 	md.fetchQueryResults_testUsers(path
						,"/Users/shreyas/Downloads/index/"
						,100
						,analyzer
						,similarity);

				writer.append(md.runTrecEval(returnFile.split(" ")[0],(returnFile.split(" ")[1])));
				counter += 1;
			}
		}

		writer.flush();
		writer.close();

	}

	//------------------------------------------------------------------------------------------------------------------------
	// Method: fetchUser - FetchUsers from reviews
	//------------------------------------------------------------------------------------------------------------------------
	public void fetchUsers(String path, String searchCity, String searchState) throws FileNotFoundException, IOException{
		buz = new BufferedReader(new FileReader(path + "business.json"));
		rev = new BufferedReader(new FileReader(path + "review.json"));
		usr = new BufferedReader(new FileReader(path + "user.json"));
		String buzLine, revLine, usrLine, businessId, userId, buzName, usrName;

		HashSet<String> cityUserId = new HashSet<String>();

		while ((buzLine = buz.readLine()) != null) {

			JSONObject B = new JSONObject(buzLine);
			String city = B.getString("city").trim().toLowerCase();
			String state = B.getString("state").trim().toLowerCase();

			if(city.equals(searchCity.toLowerCase().trim()) &&
					state.equals(searchState.toLowerCase().trim())) {
				businessId = B.getString("business_id").trim().toLowerCase();
				buzName = B.getString("name").trim();
				citybusinessId.add(businessId);
				businessId_name.put(businessId, buzName);
			}
		}

		while ((revLine = rev.readLine()) != null) {
			JSONObject R = new JSONObject(revLine);
			businessId = R.getString("business_id").trim().toLowerCase();
			if(citybusinessId.contains(businessId)) {
				cityUserId.add(R.getString("user_id").trim().toLowerCase());	
			}
		}
		System.out.println("Total Users w.r.t "+ searchCity + ',' + searchState + ": " + cityUserId.size());
		while ((usrLine = usr.readLine()) != null) {
			JSONObject U = new JSONObject(usrLine);
			userId = U.getString("user_id").trim().toLowerCase();
			usrName = U.getString("name").trim();
			userId_name.put(userId, usrName);
			if(cityUserId.contains(userId)) {
				if((int)U.get("review_count") <= 20) {
					cityUserId.remove(userId);					
				}												
			}
			else {
				cityUserId.remove(userId);
			}
		}
		System.out.println("Reviews >20 Users " + cityUserId.size());

		Iterator<String> ui= cityUserId.iterator();
		int splitCounter = 0;
		while(ui.hasNext()) {
			if(splitCounter <= (cityUserId.size()*.8)) {
				trainUserId.add(ui.next());
			}
			else {
				testUserId.add(ui.next());
			}
			splitCounter += 1;
		}
		assert ((testUserId.size() + trainUserId.size()) == cityUserId.size());
		buz.close();
		usr.close();
		rev.close();
	}

	//------------------------------------------------------------------------------------------------------------------------
	// Method: generateIndex_trainUsers - Here we generate the index required for the set of training Users
	//------------------------------------------------------------------------------------------------------------------------
	public void generateIndex_trainUsers(String path, String pathToIndex, Analyzer analyzer) throws FileNotFoundException, IOException{

		rev = new BufferedReader(new FileReader(path + "review.json"));
		String revLine, businessId, userId, review;

		HashMap<String, String> buzReview = new HashMap<String, String>(); 
		System.out.print("Fetching reviews w.r.t. Business....");
		while ((revLine = rev.readLine()) != null) {
			JSONObject B = new JSONObject(revLine);				
			userId = B.getString("user_id").trim().toLowerCase();
			businessId = B.getString("business_id").trim().toLowerCase();
			if(trainUserId.contains(userId) && citybusinessId.contains(businessId)) {
				if(!buzReview.containsKey(businessId)) {						
					buzReview.put(businessId, B.getString("text").trim());
				}
				else {
					review = buzReview.get(businessId);
					review = review + "\n" + B.getString("text").trim();
					buzReview.put(businessId, review);
				}
			}												
		}
		rev.close();
		System.out.print(".... done!\n");
		System.out.print("Generating Lucene Index....");
		// Generate Index
		Directory indexDir = FSDirectory.open(Paths.get(pathToIndex));
		IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
		iwc.setOpenMode(OpenMode.CREATE);
		IndexWriter iWriter = new IndexWriter(indexDir, iwc);
		for(HashMap.Entry<String, String> entry : buzReview.entrySet()) {
			Document doc = new Document();
			doc.add(new StringField("business_id", entry.getKey(), Field.Store.YES));
			doc.add(new TextField("review_text", entry.getValue(), Field.Store.YES));
			iWriter.addDocument(doc);		
		}
		iWriter.close();
		System.out.print(".... done!\n");
	}

	//------------------------------------------------------------------------------------------------------------------------
	// Method: fetchQueryResults_testUsers - Here we obtain the query results for the set of test Users
	//------------------------------------------------------------------------------------------------------------------------
	public String fetchQueryResults_testUsers(String path, String pathToIndex, int kResults, Analyzer analyzer, Similarity algo) 
			throws FileNotFoundException, IOException, ParseException, org.apache.lucene.queryparser.classic.ParseException, BooleanQuery.TooManyClauses{

		String keyword = "Analyzer", analyzerName = null, similarityName = null;

		Pattern ptrn = Pattern.compile("\\w+" + keyword);
		Matcher matcher = ptrn.matcher(analyzer.toString());
		if (matcher.find()) { analyzerName = matcher.group(0); }

		if(algo.toString().substring(0,2).equals("BM")) {
			similarityName = "BM25";
		} else if(algo.toString().substring(0,4).equals("LM D")) {
			similarityName = "LM_Dirichlet";
		} else if(algo.toString().substring(0,4).equals("LM J")) {
			similarityName = "LM_Jelinek_Mercer";
		}else{ 
			similarityName = "Classic"; 
		}


		String groundTruthFile = "/Users/shreyas/Downloads/"+ analyzerName + similarityName +"groundTruth.txt";
		String resultFile = "/Users/shreyas/Downloads/"+ analyzerName + similarityName +"result.txt";
		rev = new BufferedReader(new FileReader(path + "review.json"));
		String revLine, businessId, userId, review;

		HashMap<String, String> userReview = new HashMap<String, String>();
		HashMap<String, HashSet<String>> userBuz = new HashMap<String, HashSet<String>>();
		HashMap<String, HashSet<String>> Results = new HashMap<String, HashSet<String>>();
		System.out.print("Fetching reviews & Businesses w.r.t. Users....");
		while ((revLine = rev.readLine()) != null) {
			JSONObject B = new JSONObject(revLine);				
			userId = B.getString("user_id").trim().toLowerCase();
			businessId = B.getString("business_id").trim().toLowerCase();
			if(testUserId.contains(userId) && citybusinessId.contains(businessId)) {
				if(!userReview.containsKey(userId)) {						
					userReview.put(userId, B.getString("text").trim());
				}
				else {
					review = userReview.get(userId);
					review = review + "\n" + B.getString("text").trim();
					userReview.put(userId, review);
				}
				if(!userBuz.containsKey(userId)) {
					HashSet<String> buz = new HashSet<String>();
					buz.add(businessId);
					userBuz.put(userId, buz);
				}
				else {
					HashSet<String> buz = userBuz.get(userId);
					buz.add(businessId);
					userBuz.put(userId, buz);
				}
			}												
		}
		rev.close();
		System.out.print("... done!\n");

		// generate ground truth
		System.out.print("Generating ground truth....");
		PrintWriter groundWriter = new PrintWriter(groundTruthFile, "UTF-8");
		for(HashMap.Entry<String, HashSet<String>> entry : userBuz.entrySet()) {

			Iterator<String> bi = entry.getValue().iterator();
			while(bi.hasNext()) {
				String buzId = bi.next();
				groundWriter.println(entry.getKey() + " 0 " + buzId + " 1 ");
				//				System.out.println(entry.getKey() + " " + userId_name.get(entry.getKey()) + " " + buzId  + " " + businessId_name.get(buzId));
			}

			HashSet<String> difSet = citybusinessId;
			difSet.removeAll(entry.getValue());
			Iterator<String> dbi = difSet.iterator();
			while(dbi.hasNext()) {
				groundWriter.println(entry.getKey() + " 0 " + dbi.next() + " 0 ");
			}			
		}
		groundWriter.flush();
		groundWriter.close();
		System.out.print("... done!\n");

		// generate results
		System.out.print("Generating results....");
		PrintWriter resultWriter = new PrintWriter(resultFile, "UTF-8");
		IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(pathToIndex)));
		IndexSearcher searcher = new IndexSearcher(reader);
		MultiFieldQueryParser parser = new MultiFieldQueryParser(new String[]{"review_text"}, analyzer);
		searcher.setSimilarity(algo);

		for(HashMap.Entry<String, String> entry : userReview.entrySet()) {

			HashSet<String> resultBusinessId = new HashSet<String>();

			Query query = parser.parse(QueryParser.escape(entry.getValue()));

			Integer currMaxClause = BooleanQuery.getMaxClauseCount();
			Integer newMaxClause = currMaxClause + 10240;
			BooleanQuery.setMaxClauseCount(newMaxClause);
			TopDocs topDocs = searcher.search(query, kResults);
			ScoreDoc[] scoreDocs = topDocs.scoreDocs;


			for (int i = 0; i < scoreDocs.length; i++) {				
				Document doc = searcher.doc(scoreDocs[i].doc);
				resultBusinessId.add(doc.get("business_id"));
				resultWriter.println(entry.getKey() + " 0 " + doc.get("business_id")  + " " + ((int)i+1) + " " + scoreDocs[i].score + " " + analyzerName+"|"+similarityName);
				//					System.out.println(entry.getKey() + " " + userId_name.get(entry.getKey()) + " " + doc.get("business_id")  + " " + businessId_name.get(doc.get("business_id")) + " " + scoreDocs[i].score);
			}
			Results.put(entry.getKey(), resultBusinessId);
		}
		resultWriter.flush();
		resultWriter.close();
		System.out.print("...done!\n");
		return groundTruthFile + " " + resultFile;
	}

	//------------------------------------------------------------------------------------------------------------------------
	// Method: runTrecEval - Here we run the TREC EVAL module to obatin the model statistics
	//------------------------------------------------------------------------------------------------------------------------
	public String runTrecEval(String groundTruthFile, String resultFile) throws IOException, TooManyClauses, ParseException {

		String writeLine = "runid,map,recip_rank,P_5,P_10,P_15,P_20,P_100,recall_5,recall_10,recall_15,recall_20,recall_100\n";

		String trecEvalLines = null;

		// using the Runtime exec method:
		String command = "/Users/shreyas/Downloads/trec_eval.9.0/trec_eval -m all_trec" + " " + groundTruthFile + " " + resultFile;
		Process p = Runtime.getRuntime().exec(command);

		BufferedReader stdInput = new BufferedReader(new 
				InputStreamReader(p.getInputStream()));

		BufferedReader stdError = new BufferedReader(new 
				InputStreamReader(p.getErrorStream()));

		while ((trecEvalLines = stdInput.readLine()) != null) {

			String [] trecEvalLine = trecEvalLines.split("\t");
			//					System.out.println(trecEvalLine[0] + "|" + trecEvalLine[1] + "|" + trecEvalLine[2] );
			if(trecEvalLine[0].trim().equals("runid")) {
				writeLine += trecEvalLine[2] + ",";
			}else if(trecEvalLine[0].trim().equals("map")) {
				writeLine += trecEvalLine[2] + ",";
			}else if(trecEvalLine[0].trim().equals("recip_rank")) {
				writeLine += trecEvalLine[2] + ",";
			}else if(trecEvalLine[0].trim().equals("P_5")) {
				writeLine += trecEvalLine[2] + ",";
			}else if(trecEvalLine[0].trim().equals("P_10")) {
				writeLine += trecEvalLine[2] + ",";
			}else if(trecEvalLine[0].trim().equals("P_15")) {
				writeLine += trecEvalLine[2] + ",";
			}else if(trecEvalLine[0].trim().equals("P_20")) {
				writeLine += trecEvalLine[2] + ",";
			}else if(trecEvalLine[0].trim().equals("P_100")) {
				writeLine += trecEvalLine[2] + ",";
			}else if(trecEvalLine[0].trim().equals("recall_5")) {
				writeLine += trecEvalLine[2] + ",";
			}else if(trecEvalLine[0].trim().equals("recall_10")) {
				writeLine += trecEvalLine[2] + ",";
			}else if(trecEvalLine[0].trim().equals("recall_15")) {
				writeLine += trecEvalLine[2] + ",";
			}else if(trecEvalLine[0].trim().equals("recall_20")) {
				writeLine += trecEvalLine[2] + ",";
			}else if(trecEvalLine[0].trim().equals("recall_100")) {
				writeLine += trecEvalLine[2];
			}				
		}


		// read any errors from the attempted command
		System.out.println("Here is the standard error of the command (if any):\n");
		while ((trecEvalLines = stdError.readLine()) != null) {
			System.out.println(trecEvalLines);
		}
		return writeLine + "\n";
	}


}