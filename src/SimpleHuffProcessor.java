import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

public class SimpleHuffProcessor implements IHuffProcessor {

	private static final String BASE = "/Users/Cannon/Documents/workspace/Comp Sci 201/compsci201_Huffman/";
	private HuffViewer viewer;

	private BitInputStream input;
	private BitOutputStream output;

	private int[] counts;
	private Map<Integer, String> encodingMap;
	private PriorityQueue<TreeNode> pq;

	private int bitsSaved;

	public static void main(String[] args) throws IOException {
		FileInputStream in = new FileInputStream(BASE + "data/test/singleword.txt");
		IHuffProcessor huff = new SimpleHuffProcessor();
		huff.preprocessCompress(in);
		huff.compress(in, System.out, true);
	}

	public void update(String s) {
		viewer.update(s, false);
	}

	@Override
	public void setViewer(HuffViewer viewer) {
		this.viewer = viewer;	
	}

	@Override
	public int preprocessCompress(InputStream in) throws IOException {
		input = new BitInputStream(in);
		counts = countFreq();

		pq = buildPQ(counts);
		TreeNode root = reduceForest(pq);
		buildEncodingMap(root);
		bitsSaved = getBitsSaved(root);
		return bitsSaved;
	}

	@Override
	public int compress(InputStream in, OutputStream out, boolean force)
			throws IOException {
		if (!force && bitsSaved <= 0) {
			throw new IOException("Compression does not save any space");
		}

		input = new BitInputStream(in);
		output = new BitOutputStream(out);
		int numBits = 0;

		//write out the magic number
		output.writeBits(BITS_PER_INT, MAGIC_NUMBER);
		output.writeBits(BITS_PER_INT, STORE_COUNTS);
		numBits += 2 * BITS_PER_INT;

		//write character frequencies (header)
		for (int i = 0; i < counts.length; i++) {
			output.writeBits(BITS_PER_INT, counts[i]);
			numBits += BITS_PER_INT;
		}

		//write encoded file
		int inBits;
		while ((inBits = input.readBits(BITS_PER_WORD)) != -1) {
			String encoding = encodingMap.get(inBits);
			for (char b : encoding.toCharArray()) {
				if (b == '1') {
					output.writeBits(1, 1);
				} else {
					output.writeBits(1, 0);
				}
				numBits++;
			}
		}

		//write PSEUDO_EOF
		String eof = encodingMap.get(PSEUDO_EOF);
		for (char b : eof.toCharArray()) {
			if (b == '1') {
				output.writeBits(1, 1);
			} else {
				output.writeBits(1, 0);
			}
			numBits++;
		}

		return numBits;
	}

	@Override
	public int uncompress(InputStream in, OutputStream out) throws IOException {
		input = new BitInputStream(in);
		output = new BitOutputStream(out);

		//check if file is one we compressed
		int magic = input.readBits(BITS_PER_INT);
		if (magic != MAGIC_NUMBER){
			throw new IOException("magic number not right");
		}
		int format = input.readBits(BITS_PER_INT);
		if (format != STORE_COUNTS) {
			throw new IOException("can only uncompress STORE_COUNT format");
		}

		counts = new int[ALPH_SIZE];
		for (int i = 0; i < ALPH_SIZE; i++) {
			counts[i] = input.readBits(BITS_PER_INT);
		}

		PriorityQueue<TreeNode> pq = buildPQ(counts);
		TreeNode root = reduceForest(pq);
		buildEncodingMap(root);
		int numBits = 0;

		// traverse
		int inBits = input.readBits(1);
		TreeNode current = root;
		while (inBits != -1 && current.myValue != PSEUDO_EOF) {
			if (current.myLeft == null && current.myRight == null) {
				output.writeBits(BITS_PER_WORD, current.myValue);
				numBits += BITS_PER_WORD;
				current = root;
			} else {
				if (inBits == 0) { // go left
					current = current.myLeft;
				} else { // go right
					current = current.myRight;
				}
				inBits = input.readBits(1);

			}
		}
		return numBits;
	}

	private int[] countFreq() throws IOException {
		int[] counts = new int[ALPH_SIZE];
		int inBits;

		while ((inBits = input.readBits(BITS_PER_WORD)) != -1) {
			counts[inBits]++;
		}
		return counts;
	}

	private PriorityQueue<TreeNode> buildPQ(int[] counts) {
		PriorityQueue<TreeNode> pq = new PriorityQueue<TreeNode>();
		for (int i = 0; i < counts.length; i++) {
			int weight = counts[i];
			if (weight != 0) {
				TreeNode node = new TreeNode(i, weight);
				pq.add(node);
			}
		}
		pq.add(new TreeNode(PSEUDO_EOF, 1));
		return pq;
	}

	private TreeNode reduceForest(PriorityQueue<TreeNode> forest) {
		while (forest.size() > 1) {
			TreeNode one = forest.poll();
			TreeNode two = forest.poll();
			TreeNode parent = new TreeNode(-1, one.myWeight + two.myWeight, one, two);
			forest.add(parent);
		}
		return forest.remove();
	}

	private void buildEncodingMap(TreeNode root) {
		encodingMap = new HashMap<Integer, String>();
		encodingHelper(root.myLeft, "0");
		encodingHelper(root.myRight, "1");
	}

	private void encodingHelper(TreeNode node, String encoding) {
		if (node == null) return;

		if (node.myValue != -1) {
			encodingMap.put(node.myValue, encoding);
		} else {
			encodingHelper(node.myLeft, encoding + "0");
			encodingHelper(node.myRight, encoding + "1");
		}
	}

	private int getBitsSaved(TreeNode root) {
		int origBits = root.myWeight * BITS_PER_WORD;
		int huffBits = BITS_PER_INT * (2 + ALPH_SIZE);
		for (int key : encodingMap.keySet()) {
			String encoding = encodingMap.get(key);
			if (key != PSEUDO_EOF) {
				huffBits += encoding.length() * counts[key];
			} else {
				huffBits += encoding.length(); // * 1
			}
		}
		return origBits - huffBits;
	}
}
