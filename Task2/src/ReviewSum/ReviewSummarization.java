package ReviewSum;
import java.util.regex.*;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.text.BreakIterator;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections15.Transformer;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
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
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.json.JSONObject;
import org.json.simple.JsonObject;

import edu.uci.ics.jung.algorithms.scoring.PageRank;
import edu.uci.ics.jung.graph.DirectedSparseGraph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import edu.uci.ics.jung.graph.util.EdgeType;
public class ReviewSummarization {
	//Global Hash Map for storing idfs in order to avoid recalculating idf for the same word
	public static HashMap<String, Double> idfWord = new HashMap<String, Double>();
	public static void printMap(Map mp, int count, Map mp2) {
		Iterator it = mp.entrySet().iterator();
		int c = 0;
		while (it.hasNext()) {
			Map.Entry pair = (Map.Entry)it.next();
			System.out.println(mp2.get(pair.getKey()));
			it.remove(); // avoids a ConcurrentModificationException
			c+=1;
			if (c>count){
				System.exit(0);
			}
		}
	}

	public static void printMap(Map mp) {
		Iterator it = mp.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry pair = (Map.Entry)it.next();
			System.out.println(pair.getKey() + " = " + pair.getValue());
			it.remove(); // avoids a ConcurrentModificationException
		}
	}

	public static void main(String[] args) throws Exception {

		ReviewSummarization rs = new ReviewSummarization();
		HashMap<String, String> buzReview = new HashMap<String, String>();
		buzReview = rs.getBZReview();
		//Sample List of business ids to try out:
		//gngjelug4emjcyrhhlvp7q
		//g_fgpzjlgekglgc-j-tj6w
		//w_ucgmgok7n9p0xdybx1vq
		//yrk8ezafv59hdhsuhiiu6q
		//8ridfnwhso-k0xb9y1gjra
		//pghyyl4grwsg-bau3laysw
		//3kpqtml3edfhcwxfiht_iq
		//3lxibz4yhsjeqrbdwox28w
		//tuluhfymvbkyhsjmn30a9w
		//7hup4xxmucgqvpfam8ijww
		HashMap<Integer, String> sentMap= rs.getSentMap(buzReview.get("3lxibz4yhsjeqrbdwox28w"));
		//HashMap<Integer, String> sentMap= rs.getSentMap(text);
		Map<Integer, Double> sortedResults = new HashMap<Integer, Double>();
		//sortedResults = rs.lexCentrality(sentMap);
		sortedResults = rs.continuousLexRank(sentMap);
		//printMap(sentMap);
		//System.exit(0);
		System.out.println("Final ans");
		printMap(sortedResults, 5, sentMap);
		System.out.println("Done final");
		//rs.createIndex(buzReview);

	}
	//generates a HashMap mapping businness ids to corresponding combined review text
	// author: Vighnesh Nayak
	public HashMap<String, String> getBZReview() throws Exception{
		BufferedReader rev = new BufferedReader(new FileReader("D:\\search\\yelp-dataset.tar\\dataset\\review.json"));
		HashMap<String, String> buzReview = new HashMap<String, String>(); 
		String revLine, businessId,review;
		int count = 0;
		System.out.print("Fetching reviews w.r.t. Business....");
		while ((revLine = rev.readLine()) != null) {
			JSONObject B = new JSONObject(revLine);				
			businessId = B.getString("business_id").trim().toLowerCase();
			System.out.println(businessId + "  " + (++count));
			if(!buzReview.containsKey(businessId)) {						
				buzReview.put(businessId, B.getString("text").trim());
			}
			else {
				review = buzReview.get(businessId);
				review = review + "\n" + B.getString("text").trim();
				buzReview.put(businessId, review);
			}
			if (count > 10000) {
				break;
			}

		}
		rev.close();
		System.out.println(" Hash Map Done");
		return buzReview;
	}

	public void createIndex(HashMap<String, String> buzReview) throws Exception {
		String indexPath = "D:\\search\\restaurantIndex\\";
		System.out.println("Index start");
		Directory indexDir = FSDirectory.open(Paths.get(indexPath));
		Analyzer analyzer = new StandardAnalyzer();
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
		System.out.println("Index stop");
	}
	//outputs a HashMap mapping sentences to their positions in the review text
	// generates index considering each sentence as a document
	// This is done to get importance of sentence as per the review Text it occurs in and not the entire corpus.
	// author : Prathamesh Jangam
	public HashMap<Integer, String> getSentMap(String reviewText) throws Exception{
		String indexPath = "D:\\search\\restaurantIndex\\";
		System.out.println("Index start");
		Directory indexDir = FSDirectory.open(Paths.get(indexPath));
		Analyzer analyzer = new StandardAnalyzer(CharArraySet.EMPTY_SET);
		IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
		iwc.setOpenMode(OpenMode.CREATE);
		IndexWriter iWriter = new IndexWriter(indexDir, iwc);
		BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.US);
		iterator.setText(reviewText);
		HashMap<Integer, String> sentMap = new HashMap<Integer, String>();
		int start = iterator.first();
		int sentCount = 0;
		for (int end = iterator.next();
				end != BreakIterator.DONE;
				start = end, end = iterator.next()) {
			Document doc = new Document();
			doc.add(new StringField("sent_id", Integer.toString(sentCount), Field.Store.YES));
			doc.add(new TextField("review_text", reviewText.substring(start,end), Field.Store.YES));
			iWriter.addDocument(doc);
			sentMap.put(sentCount, reviewText.substring(start,end));
			sentCount++;
		}
		iWriter.close();
		System.out.println("SentMap done");
		return sentMap;
	}

	public double getIDF(String word, HashMap<Integer, String> sentMap) throws Exception {
		String indexPath = "D:\\search\\restaurantIndex\\";
		IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPath)));
		double df=reader.docFreq(new Term("review_text", word));
		double Docs = reader.maxDoc();
		double idf = Math.log(1+(Docs/(1 + df)));
		idfWord.put(word, idf);
		return idf;
	}
	// outputs idf-modified-cosine similarity between sentences
	//author: Vighnesh Nayak
	public double compareSents(String sent1, String sent2, HashMap<Integer, String> sentMap) throws Exception {
		String indexPath = "D:\\search\\restaurantIndex\\";
		String[] sent1array = sent1.split("\\s+");
		String[] sent2array = sent2.split("\\s+");
		//IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPath)));
		//IndexSearcher searcher = new IndexSearcher(reader);
		HashMap<String, Integer> wordFreq1 = getWordFreq(sent1array);
		//System.out.println("Hashmap1");
		//printMap(wordFreq1);
		HashMap<String, Integer> wordFreq2 = getWordFreq(sent2array);
		//System.out.println("Hashmap2");
		//printMap(wordFreq2);
		//System.exit(0);
		double idf = 0;
		double x = 0;
		for(Entry<String, Integer> entry:wordFreq1.entrySet()) {
			String word = entry.getKey();

			if (idfWord.containsKey(word)) {
				idf = idfWord.get(word);
			}
			else {
				idf = getIDF(word, sentMap);
			}
			//System.out.println(word+" "+idf);
			x += Math.pow(entry.getValue()*idf,2);
		}
		double y = 0;
		for(Entry<String, Integer> entry:wordFreq2.entrySet()) {
			String word = entry.getKey();
			if (idfWord.containsKey(word)) {
				idf = idfWord.get(word);
			}
			else {
				idf = getIDF(word, sentMap);
			}
			//System.out.println(word+" "+idf);
			y += Math.pow(entry.getValue()*idf,2);
		}
		Set<String> common = new HashSet<String>(wordFreq1.keySet());
		common.retainAll(wordFreq2.keySet());
		double num = 0.0;
		if (common.size()==0) {
			return 0;
		}
		for(String word:common) {
			if (idfWord.containsKey(word)) {
				idf = idfWord.get(word);
			}
			else {
				idf = getIDF(word, sentMap);
			}
			num+=wordFreq1.get(word)*wordFreq2.get(word)*Math.pow(idf,2);
		}
		//System.out.println("x"+x);
		//System.out.println("y"+y);
		return (double)num/Math.sqrt(x*y);



	}
	// outputs HashMap mapping words to their respective frequencies in a given sentence
	// author: Vighnesh Nayak
	public HashMap<String,Integer> getWordFreq(String[] sent){
		HashMap<String, Integer> wordFreq = new HashMap<String, Integer>();
		int wordCount;
		for(int i=0; i< sent.length; i++) {
			String word = sent[i].trim().toLowerCase().replaceAll("[^a-zA-Z]+$", "");
			word = word.trim();
			if(word.length() > 0) {
				if(wordFreq.containsKey(word)) {
					wordCount = wordFreq.get(word);
					wordCount++;
					wordFreq.put(word, wordCount);
				}
				else {
					wordFreq.put(word, 1);
				}
			}

		}
		return wordFreq;
	}
	// sorts a Map in descending order of its values
	// author: Prathamesh Jangam
	public static <K, V extends Comparable<? super V>> Map<K, V> sortByValue(Map<K, V> map) {
		return map.entrySet().stream().sorted(Map.Entry.comparingByValue(Collections.reverseOrder()))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
	}

	//LexRank implementation using Lexical Centrality
	//author: Prathamesh Jangam
	public Map<Integer, Double> lexCentrality(HashMap<Integer, String> sentMap) throws Exception {


		UndirectedSparseGraph<Integer, String> graph = new UndirectedSparseGraph<Integer, String>();
		int numberOfVertices = sentMap.size();
		double[][] weights = new double[numberOfVertices][numberOfVertices];
		for(int i=0;i<numberOfVertices;i++) {
			for(int j=0;j<numberOfVertices;j++) {
				weights[i][j] = 0;
			}
		}
		HashMap <Integer, Double>results = new HashMap<Integer, Double>();
		System.out.println(numberOfVertices+" total sentences");
		for(int i = 0;i<numberOfVertices; i++) {
			graph.addVertex(i);
		}
		double[] degrees = new double[numberOfVertices];
		for(int i=0;i<numberOfVertices;i++) {
			degrees[i] = 0;
		}
		
		for( int i =0; i<numberOfVertices; i++) {
			for(int j=0; j<numberOfVertices; j++) {
				double score = compareSents(sentMap.get(i), sentMap.get(j), sentMap);
				if(score>=0.1) {
					String tmp_edge = i+"->"+j;
					graph.addEdge(tmp_edge, i, j, EdgeType.UNDIRECTED);
					weights[i][j] =1;
					degrees[i]++;
				}
				else {
					weights[i][j] = 0;
				}

			}
			System.out.println(i+" Done with all nodes");
		}
		for(int i=0;i<numberOfVertices;i++) {
			for (int j=0;j<numberOfVertices;j++) {
				weights[i][j] = weights[i][j]/degrees[i];
			}
		}
		System.out.println("Graph and edges done");
		Transformer<String, Double> edge_weights = 
				new Transformer<String, Double>()
		{
			@Override
			public Double transform(String e) 
			{
				String[] split = e.split("->");           
				return weights[Integer.parseInt(split[0])][Integer.parseInt(split[1])];
			}           
		};
		System.out.println("Graph and edges transformation done");
		PageRank<Integer, String> PR = new PageRank<Integer, String>(graph, edge_weights, 0.85);
		PR.setMaxIterations(30);
		PR.evaluate();
		System.out.println("Pagerank done");
		for(Integer v: graph.getVertices()) {
			results.put(v, PR.getVertexScore(v));
		}
		Map<Integer,Double> sorted_results = sortByValue(results);

		return sorted_results;
	}
	//Implementation using continuous LexRank
	//author: Vighnesh Nayak
	public Map<Integer, Double> continuousLexRank(HashMap<Integer, String> sentMap) throws Exception {


		UndirectedSparseGraph<Integer, String> graph = new UndirectedSparseGraph<Integer, String>();
		int numberOfVertices = sentMap.size();
		double[][] weights = new double[numberOfVertices][numberOfVertices];
		for(int i=0;i<numberOfVertices;i++) {
			for(int j=0;j<numberOfVertices;j++) {
				weights[i][j] = 0;
			}
		}
		HashMap <Integer, Double>results = new HashMap<Integer, Double>();
		System.out.println(numberOfVertices+" total sentences");
		for(int i = 0;i<numberOfVertices; i++) {
			graph.addVertex(i);
		}
		double[] degrees = new double[numberOfVertices];
		for(int i=0;i<numberOfVertices;i++) {
			degrees[i] = 0;
		}
		for( int i =0; i<numberOfVertices; i++) {
			for(int j=0; j<numberOfVertices; j++) {
				double score = compareSents(sentMap.get(i), sentMap.get(j), sentMap);
				if(score!=0) {
					String tmp_edge = i+"->"+j;
					graph.addEdge(tmp_edge, i, j, EdgeType.UNDIRECTED);
					weights[i][j] = score;
					degrees[i]+=score;
				}

			}
			System.out.println(i+" Done with all nodes");
		}
		System.out.println("Graph and edges done");
		
		for(int i=0;i<numberOfVertices;i++) {
			for (int j=0;j<numberOfVertices;j++) {
				weights[i][j] = weights[i][j]/degrees[i];
			}
		}
		System.out.println("Graph and edges done");
		Transformer<String, Double> edge_weights = 
				new Transformer<String, Double>()
		{
			@Override
			public Double transform(String e) 
			{
				String[] split = e.split("->");           
				return weights[Integer.parseInt(split[0])][Integer.parseInt(split[1])];
			}           
		};
		System.out.println("Graph and edges transformation done");
		PageRank<Integer, String> PR = new PageRank<Integer, String>(graph, edge_weights, 0.85);
		PR.setMaxIterations(30);
		PR.evaluate();
		System.out.println("Pagerank done");
		for(Integer v: graph.getVertices()) {
			results.put(v, PR.getVertexScore(v));
		}
		Map<Integer,Double> sorted_results = sortByValue(results);

		return sorted_results;
	}


}
