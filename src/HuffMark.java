import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashSet;
import java.util.Set;

import javax.swing.JFileChooser;

public class HuffMark {
	protected static JFileChooser ourOpenChooser = new JFileChooser(System
			.getProperties().getProperty("user.dir"));
	static {
		ourOpenChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
	}

	private double myTotalCompressTime;
	private long myTotalUncompressedBytesBeforeCompression;
	private long myTotalCompressedBytesAfterCompression;

	private double myTotalUncompressTime;
	private long myTotalUncompressedBytesAfterUncompression;

	private IHuffProcessor myHuffer;

	private Set<File> filesToDelete;

	private static String COMPRESSED_SUFFIX = ".hf";
	private static String UNCOMPRESSED_SUFFIX = ".unhf";
	private static boolean FAST_READER = true;

	public void compress(File f) throws IOException{

		if (f.getName().endsWith(COMPRESSED_SUFFIX)) return;  // don't read .hf files!
		if (f.getName().endsWith(UNCOMPRESSED_SUFFIX)) return; // don't read .unhf files!
		if (f.isDirectory()) return; // don't read directories

		// compress
		double start = System.currentTimeMillis();
		myHuffer.preprocessCompress(getFastByteReader(f));
		File outFile = new File(getCompressedName(f));
		FileOutputStream out = new FileOutputStream(outFile);
		System.out.println("compressing to: "+outFile.getCanonicalPath());
		myHuffer.compress(getFastByteReader(f), out,true);
		double end = System.currentTimeMillis();
		double time = (end-start)/1000.0;

		myTotalUncompressedBytesBeforeCompression += f.length();
		myTotalCompressedBytesAfterCompression += outFile.length();
		myTotalCompressTime += time;
		double compressionPercentage = 100.0* (1.0 - 1.0 * outFile.length() / f.length());

		filesToDelete.add(outFile);

		System.out.printf("%s from\t %d bytes to\t %d bytes\t(%.3f %%)\t in\t %.3fms\n",
				f.getName(),
				f.length(),
				outFile.length(),
				compressionPercentage,
				time);
	}

	public void uncompress(File f) throws IOException {
		if (!f.getName().endsWith(COMPRESSED_SUFFIX)) return; // don't try to uncompress non-compressed files
		if (f.getName().endsWith(UNCOMPRESSED_SUFFIX)) return; // don't try to uncompress .unhf files
		if (f.isDirectory()) return; // don't try to uncompress directories

		// uncompress
		double start = System.currentTimeMillis();
		File outFile = new File(getUncompressedName(f));
		FileOutputStream out = new FileOutputStream(outFile);
		System.out.println("uncompressing to: " + outFile.getCanonicalPath());
		myHuffer.uncompress(getFastByteReader(f), out);
		double end = System.currentTimeMillis();
		double time = (end - start) / 1000.0;

		myTotalUncompressedBytesAfterUncompression += outFile.length();
		myTotalUncompressTime += time;

		filesToDelete.add(outFile);

		System.out.printf("%s from\t %d bytes to\t %d bytes in\t %.3fms\n",
				f.getName(),
				f.length(),
				outFile.length(),
				time);
		System.out.println();
	}

	public void compressTwice(File f) throws IOException{

		if (f.getName().endsWith(COMPRESSED_SUFFIX)) return;  // don't read .hf files!
		if (f.getName().endsWith(UNCOMPRESSED_SUFFIX)) return; // don't read .unhf files!
		if (f.isDirectory()) return; // don't read directories
		
		myTotalUncompressedBytesBeforeCompression += f.length();

		double start = System.currentTimeMillis();
		myHuffer.preprocessCompress(getFastByteReader(f));
		File outFile = new File(getCompressedName(f));
		FileOutputStream out = new FileOutputStream(outFile);
		System.out.println("compressing to: "+outFile.getCanonicalPath());
		myHuffer.compress(getFastByteReader(f), out,true);
		double end = System.currentTimeMillis();
		double time = (end-start)/1000.0;

		System.out.printf("first compress:\t%s from\t %d to\t %d in\t %.3f\n",f.getName(),f.length(),outFile.length(),time);
		
		start = System.currentTimeMillis();
		f = outFile;
		myHuffer.preprocessCompress(getFastByteReader(f));
		outFile = new File(getCompressedName(f));
		out = new FileOutputStream(outFile);
		System.out.println("compressing to: "+outFile.getCanonicalPath());
		myHuffer.compress(getFastByteReader(f), out,true);
		end = System.currentTimeMillis();
		time = (end-start)/1000.0;
		
		myTotalCompressedBytesAfterCompression += outFile.length();
		myTotalCompressTime += time;
		
		filesToDelete.add(f);
		filesToDelete.add(outFile);
		
		System.out.printf("second compress:\t%s from\t %d to\t %d in\t %.3f\n",f.getName(),f.length(),outFile.length(),time);

	}


	public void doMark() throws IOException{
		if (myHuffer == null){
			myHuffer = new TreeHuffProcessor();
		}
		filesToDelete = new HashSet<File>();
		int action = ourOpenChooser.showOpenDialog(null);
		if (action == JFileChooser.APPROVE_OPTION){
			File dir = ourOpenChooser.getSelectedFile();
			File[] list = dir.listFiles();
			for(File f : list){
				if (f.isHidden()) continue;
				compress(f);
				uncompress(getCompressedFile(f));
			}
			System.out.println("--------");
			System.out.printf("total bytes read: %d\n",myTotalUncompressedBytesBeforeCompression);
			System.out.printf("total compressed bytes: %d\n", myTotalCompressedBytesAfterCompression);
			System.out.printf("total uncompressed bytes: %d\n", myTotalUncompressedBytesAfterUncompression);
			System.out.printf("total percent compression: %.3f %%\n",100.0* (1.0 - 1.0*myTotalCompressedBytesAfterCompression/myTotalUncompressedBytesBeforeCompression));
			System.out.printf("compression time: %.3fms\n",myTotalCompressTime);
			System.out.printf("uncompression time: %.3fms\n", myTotalUncompressTime);
		}

		deleteFiles();
	}
	
	public void doCompressTwice() throws IOException {
		if (myHuffer == null){
			myHuffer = new TreeHuffProcessor();
		}
		filesToDelete = new HashSet<File>();
		int action = ourOpenChooser.showOpenDialog(null);
		if (action == JFileChooser.APPROVE_OPTION){
			File dir = ourOpenChooser.getSelectedFile();
			File[] list = dir.listFiles();
			for(File f : list){
				if (f.isHidden()) continue;
				compressTwice(f);
			}
			System.out.println("--------");
			System.out.printf("total bytes read: %d\n",myTotalUncompressedBytesBeforeCompression);
			System.out.printf("total compressed bytes: %d\n", myTotalCompressedBytesAfterCompression);
			System.out.printf("total percent compression: %.3f %%\n",100.0* (1.0 - 1.0*myTotalCompressedBytesAfterCompression/myTotalUncompressedBytesBeforeCompression));
			System.out.printf("compression time: %.3fms\n",myTotalCompressTime);
		}

		deleteFiles();
	}

	public static void main(String[] args) throws IOException{
		HuffMark hf = new HuffMark();
		hf.doCompressTwice();
	}

	private void deleteFiles() {
		for (File f : filesToDelete) {
			f.delete();
		}
	}

	private String getCompressedName(File f){
		String name = f.getName();
		String path = null;
		try {
			path = f.getCanonicalPath();
		} catch (IOException e) {
			System.err.println("trouble with file canonicalizing "+f);
			return null;
		}
		int pos = path.lastIndexOf(name);
		String newName = path.substring(0, pos) + name + COMPRESSED_SUFFIX;
		return newName;
	}

	private File getCompressedFile(File f) {
		String compName = getCompressedName(f);
		return new File(compName);
	}

	private String getUncompressedName(File f) {
		String name = f.getName();
		String path = null;
		try {
			path = f.getCanonicalPath();
		} catch (IOException e) {
			System.err.println("trouble with file canonicalizing "+f);
			return null;
		}
		int pos = path.lastIndexOf(name);
		String newName = path.substring(0, pos) + name + UNCOMPRESSED_SUFFIX;
		return newName;
	}

	private InputStream getFastByteReader(File f) throws FileNotFoundException{

		if (!FAST_READER){
			return new FileInputStream(f);
		}

		ByteBuffer buffer = null;
		try {
			FileInputStream fis = new FileInputStream(f);
			FileChannel channel = fis.getChannel();
			buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
			byte[] barray = new byte[buffer.limit()];

			if (barray.length != channel.size()){               
				System.err.println(String.format("Reading %s error: lengths differ %d %ld\n",f.getName(),barray.length,channel.size()));
			}
			buffer.get(barray);
			channel.close();
			fis.close();
			return new ByteArrayInputStream(barray);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

}
