package com.igsl;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.csv.QuoteMode;

/**
 * Wrapper for CSVPrinter to sanitize CSV data.
 * 
 * Make sure data does not start with:
 * =
 * +
 * -
 * @
 * Tab
 * Newline
 * 
 * Data is always surrounded by double quotes.
 * Double quotes in data are doubled. 
 * 
 * Reference: 
 * https://owasp.org/www-community/attacks/CSV_Injection
 */
public class CSV {
	private static final Pattern INVALID_START_CHAR = Pattern.compile("^[=+\\-@\\t\\r\\n]+(.*)");
	private static final String CHARSET = "UTF-8";
	
	public static FileWriter getCSVFileWriter(Path path) throws IOException {
		return new FileWriter(path.toFile(), Charset.forName(CHARSET));
	}
	
	public static FileWriter getCSVFileWriter(String path) throws IOException {
		return new FileWriter(path, Charset.forName(CHARSET));
	}

	public static FileReader getCSVFileReader(Path path) throws IOException {
		return new FileReader(path.toFile(), Charset.forName(CHARSET));
	}
	
	public static FileReader getCSVFileReader(String path) throws IOException {
		return new FileReader(path, Charset.forName(CHARSET));
	}
	
	public static CSVFormat getCSVReadFormat() {
		CSVFormat fmt = CSVFormat.EXCEL;
		return fmt.builder()
			.setDelimiter(",")
			.setQuoteMode(QuoteMode.ALL)
			.setHeader()
			.build();
	}
	
	public static CSVFormat getCSVWriteFormat(Collection<String> headers) {
		CSVFormat fmt = CSVFormat.EXCEL;
		return fmt.builder()
			.setDelimiter(",")
			.setQuoteMode(QuoteMode.ALL)
			.setHeader(headers.toArray(new String[0]))
			.build();
	}
	
	public static void printRecord(CSVPrinter printer, Collection<?> args) throws IOException {
		printer.printRecord(args.toArray(new Object[0]));
	}
	
	public static Map<String, String> readMapping(CSVParser parser, String keyCol, String valueCol) throws IOException {
		Map<String, String> result = new HashMap<>();
		parser.forEach(new Consumer<CSVRecord>() {
			@Override
			public void accept(CSVRecord r) {
				String key = r.get(keyCol);
				String value = r.get(valueCol);
				result.put(key, value);
			}
		});
		return result;
	}
	
	public static void printRecord(CSVPrinter printer, Object... args) throws IOException {
		List<Object> newArgs = new ArrayList<>();
		for (Object o : args) {
			if (o != null && o instanceof String) {
				String s = (String) o;
				Matcher m = INVALID_START_CHAR.matcher(s);
				if (m.matches()) {
					s = m.group(1);
				} 
				newArgs.add(s);
			} else {
				newArgs.add(o);
			}
		}
		printer.printRecord(newArgs.toArray(new Object[0]));
	}
	
}
