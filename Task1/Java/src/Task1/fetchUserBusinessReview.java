//---------------------------------------------------------------------------
// Filename: fetchUserBusinessReview.java
// Objective: To generate the matrix for the memory based collaborative filtering
//            We apply a sentiment analysis technique as well as a mean review rating 
//            method
// INPUT: business.JSON, User.JSON, review.JSON
// OUTPUT: 
//---------------------------------------------------------------------------

package Task1;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.SimpleAnalyzer;
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
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.SentinelIntSet;
import org.json.JSONObject;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.neural.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations;
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations.SentimentAnnotatedTree;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.CoreMap;

public class fetchUserBusinessReview {

	public HashSet<String> citybusinessId = new HashSet<String>();
	public HashSet<String> cityUserId = new HashSet<String>();
	public HashMap<String, String> businessId_name = new HashMap<String, String>();
	public HashMap<String, String> userId_name = new HashMap<String, String>();
	public HashMap<List<String>, String> userBusinessReview = new HashMap<List<String>, String>(); 
	public HashMap<List<String>, Integer> userBusinessScore = new HashMap<List<String>, Integer>();
	public HashMap<List<String>, Integer> userBusinessCount= new HashMap<List<String>, Integer>();
	public HashMap<List<String>, Integer> userBusinessSentiment = new HashMap<List<String>, Integer>();	

	StanfordCoreNLP pipeline;

	private BufferedReader rev, buz, usr;

	// Method: Main 
	public static void main(String[] args) throws FileNotFoundException, IOException, ParseException, org.apache.lucene.queryparser.classic.ParseException {

		String path = "C:\\Users\\Owner\\Desktop\\Data\\dataset\\";
		String city = "Belmont";
		String state = "NC";

		fetchUserBusinessReview md = new fetchUserBusinessReview();

		md.fetchUsers(path, city, state);
		md.generateUserBusinessReview(path);
		md.generateReviewSentiment();
		md.generateUserBusinessScore(path);
	}


	//------------------------------------------------------------------------------------------------------------------------
	// Method: fetchUser - FetchUsers from reviews
	//------------------------------------------------------------------------------------------------------------------------
	public void fetchUsers(String path, String searchCity, String searchState) throws FileNotFoundException, IOException{
		buz = new BufferedReader(new FileReader(path + "business.json"));
		rev = new BufferedReader(new FileReader(path + "review.json"));
		usr = new BufferedReader(new FileReader(path + "user.json"));
		String buzLine, revLine, usrLine, businessId, userId, buzName, usrName;		

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
		buz.close();
		usr.close();
		rev.close();
	}

	//------------------------------------------------------------------------------------------------------------------------
	// Method: generateUserBusinessReview - Generate a HashMap of User Business and concatenated reviews. 
	//------------------------------------------------------------------------------------------------------------------------
	public void generateUserBusinessReview(String path) throws FileNotFoundException, IOException{

		rev = new BufferedReader(new FileReader(path + "review.json"));
		String revLine, businessId, userId, review;

		System.out.print("Fetching reviews w.r.t. userId & businessId....");
		while ((revLine = rev.readLine()) != null) {

			ArrayList<String> ubReview = new ArrayList<String>();

			JSONObject B = new JSONObject(revLine);				
			userId = B.getString("user_id").trim().toLowerCase();
			businessId = B.getString("business_id").trim().toLowerCase();

			ubReview.add(0,userId);
			ubReview.add(1,businessId);

			if(cityUserId.contains(userId) && citybusinessId.contains(businessId)) {
				if(!userBusinessReview.containsKey(ubReview)) {						
					userBusinessReview.put(ubReview, B.getString("text").trim());
				}
				else {
					review = userBusinessReview.get(ubReview);
					review = review + "\n" + B.getString("text").trim();
					userBusinessReview.put(ubReview, review);
				}
			}												
		}
		rev.close();
		System.out.print(".... done!\n");
	}

	
	//------------------------------------------------------------------------------------------------------------------------
	//Method: generateReviewSentiment - Using the Stanford core NLP package generate the 
	//------------------------------------------------------------------------------------------------------------------------
	public void generateReviewSentiment() throws IOException{		

		FileWriter writer = new FileWriter("userBusinessSentiment.csv",false);
		//		HashMap<String, List<Integer>> userAvgSentiment = new HashMap<String, List<Integer>>();

		Properties props = new Properties();
		props.setProperty("annotators", "tokenize, ssplit, parse, sentiment");
		StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

		int mainSentiment = 0;
		System.out.print("generating Sentiment w.r.t. userId & businessId....");
		System.out.print("Size " + userBusinessReview.size());

		for(HashMap.Entry<List<String>, String> entry : userBusinessReview.entrySet()) {

			int longest = 0;
			Annotation annotation = pipeline.process(entry.getValue());
			for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
				Tree tree = sentence.get(SentimentAnnotatedTree.class);
				int sentiment = RNNCoreAnnotations.getPredictedClass(tree);
				String partText = sentence.toString();
				if (partText.length() > longest) {
					mainSentiment = sentiment;
					longest = partText.length();
				}

			}
			//			System.out.println(userId_name.get(entry.getKey().get(0)) + 
			//					"\t" + businessId_name.get(entry.getKey().get(1)) +
			//					"\t" + mainSentiment);
			String writeLine = entry.getKey().get(0) + "," + entry.getKey().get(1) + "," + mainSentiment + "\n";
			writer.append(writeLine);
			userBusinessSentiment.put(entry.getKey(), mainSentiment);			
		}
		System.out.print(".... done!\n");
		writer.flush();
		writer.close();
	}


	//------------------------------------------------------------------------------------------------------------------------
	// Method: generateUserBusinessReview - Generate a HashMap of User Business and concatenated reviews. 
	//------------------------------------------------------------------------------------------------------------------------
	public void generateUserBusinessScore(String path) throws FileNotFoundException, IOException{

		rev = new BufferedReader(new FileReader(path + "review.json"));
		String revLine, businessId, userId;
		Integer score, count;

		System.out.print("Fetching reviews w.r.t. userId & businessId....");
		while ((revLine = rev.readLine()) != null) {

			ArrayList<String> ubReview = new ArrayList<String>();

			JSONObject B = new JSONObject(revLine);				
			userId = B.getString("user_id").trim().toLowerCase();
			businessId = B.getString("business_id").trim().toLowerCase();

			ubReview.add(0,userId);
			ubReview.add(1,businessId);

			if(cityUserId.contains(userId) && citybusinessId.contains(businessId)) {
				if(!userBusinessScore.containsKey(ubReview)) {		
					userBusinessCount.put(ubReview, 1);			
					userBusinessScore.put(ubReview, B.getInt("stars"));
				}
				else {
					score = userBusinessScore.get(ubReview);
					count = userBusinessCount.get(ubReview);
					score = (score*count + B.getInt("useful")) / (count + 1);
					userBusinessCount.put(ubReview, count + 1);
					userBusinessScore.put(ubReview, score);
				}
			}												
		}
		rev.close();

		// Printing Using Business score to CSV
		FileWriter writer = new FileWriter("userBusinessScore.csv",false);

		for(HashMap.Entry<List<String>, Integer> entry : userBusinessScore.entrySet()) {
			String writeLine = entry.getKey().get(0) + "," + entry.getKey().get(1) + "," + entry.getValue() + "\n";
			writer.append(writeLine);		
		}

		writer.flush();
		writer.close();

		System.out.print("\n Write.... done!\n");
	}
}
