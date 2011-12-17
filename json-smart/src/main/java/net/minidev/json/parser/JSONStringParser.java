package net.minidev.json.parser;

/*
 *    Copyright 2011 JSON-SMART authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import static net.minidev.json.parser.ParseException.ERROR_UNEXPECTED_CHAR;
import static net.minidev.json.parser.ParseException.ERROR_UNEXPECTED_DUPLICATE_KEY;
import static net.minidev.json.parser.ParseException.ERROR_UNEXPECTED_EOF;
import static net.minidev.json.parser.ParseException.ERROR_UNEXPECTED_TOKEN;
import static net.minidev.json.parser.ParseException.ERROR_UNEXPECTED_UNICODE;

import java.io.IOException;
//
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Parser for JSON text. Please note that JSONParser is NOT thread-safe.
 * 
 * @author Uriel Chemouni <uchemouni@gmail.com>
 */
class JSONStringParser extends JSONBaseParser {
	public final static byte EOI = 0x1A;
	private char c;
	private String in;
	private int len;
	
	public JSONStringParser(int permissiveMode) {
		super(permissiveMode);
	}

	/**
	 * use to return Primitive Type, or String, Or JsonObject or JsonArray
	 * generated by a ContainerFactory
	 */
	public Object parse(String in) throws ParseException {
		return parse(in, ContainerFactory.FACTORY_SIMPLE, ContentHandlerDumy.HANDLER);
	}

	/**
	 * use to return Primitive Type, or String, Or JsonObject or JsonArray
	 * generated by a ContainerFactory
	 */
	public Object parse(String in, ContainerFactory containerFactory) throws ParseException {
		return parse(in, containerFactory, ContentHandlerDumy.HANDLER);
	}

	/**
	 * use to return Primitive Type, or String, Or JsonObject or JsonArray
	 * generated by a ContainerFactory
	 */
	public Object parse(String in, ContainerFactory containerFactory, ContentHandler handler) throws ParseException {
		this.len = in.length();
		this.in = in;
		this.containerFactory = containerFactory;
		this.handler = handler;
		this.pos = -1;
		Object result;
		try {
			read();
			handler.startJSON();
			result = readMain(stopX);
			handler.endJSON();
			if (checkTaillingData) {
				skipSpace();
				if (c != EOI)
					throw new ParseException(pos - 1, ERROR_UNEXPECTED_TOKEN, c);
			}
		} catch (IOException e) {
			throw new ParseException(pos, e);
		}
		xs = null;
		xo = null;
		return result;
	}

	final private void read() {
		if (++pos >= len)
			this.c = EOI;
		else
			this.c = in.charAt(pos);
	}

	final private void readNoEnd() throws ParseException {
		if (++pos >= len) {
			this.c = EOI;
			throw new ParseException(pos - 1, ERROR_UNEXPECTED_EOF, "EOF");
		} else
			this.c = in.charAt(pos);
	}

	private List<Object> readArray() throws ParseException, IOException {
		List<Object> obj = containerFactory.createArrayContainer();
		if (c != '[')
			throw new RuntimeException("Internal Error");
		read();
		boolean needData = false;
		handler.startArray();
		for (;;) {
			switch (c) {
			case ' ':
			case '\r':
			case '\n':
			case '\t':
				read();
				continue;
			case ']':
				if (needData && !acceptUselessComma)
					throw new ParseException(pos, ERROR_UNEXPECTED_CHAR, (char) c);
				read(); /* unstack */
				handler.endArray();
				return obj;
			case ':':
			case '}':
				throw new ParseException(pos, ERROR_UNEXPECTED_CHAR, (char) c);
			case ',':
				if (needData && !acceptUselessComma)
					throw new ParseException(pos, ERROR_UNEXPECTED_CHAR, (char) c);
				read();
				needData = true;
				continue;
			case EOI:
				throw new ParseException(pos - 1, ERROR_UNEXPECTED_EOF, "EOF");
			default:
				obj.add(readMain(stopArray));
				needData = false;
				continue;
			}
		}
	}

	/**
	 * use to return Primitive Type, or String, Or JsonObject or JsonArray
	 * generated by a ContainerFactory
	 */
	private Object readMain(boolean stop[]) throws ParseException, IOException {
		for (;;) {
			switch (c) {
			// skip spaces
			case ' ':
			case '\r':
			case '\n':
			case '\t':
				read();
				continue;
				// invalid stats
			case ':':
			case '}':
			case ']':
				throw new ParseException(pos, ERROR_UNEXPECTED_CHAR, c);
				// start object
			case '{':
				return readObject();
				// start Array
			case '[':
				return readArray();
				// start string
			case '"':
			case '\'':
				xs = readString();
				handler.primitive(xs);
				return xs;
				// string or null
			case 'n':
				xs = readNQString(stop);
				if ("null".equals(xs)) {
					handler.primitive(null);
					return null;
				}
				if (!acceptNonQuote)
					throw new ParseException(pos, ERROR_UNEXPECTED_TOKEN, xs);
				handler.primitive(xs);
				return xs;
				// string or false
			case 'f':
				xs = readNQString(stop);
				if ("false".equals(xs)) {
					handler.primitive(Boolean.FALSE);
					return Boolean.FALSE;
				}
				if (!acceptNonQuote)
					throw new ParseException(pos, ERROR_UNEXPECTED_TOKEN, xs);
				handler.primitive(xs);
				return xs;
				// string or true
			case 't':
				xs = readNQString(stop);
				if ("true".equals(xs)) {
					handler.primitive(Boolean.TRUE);
					return Boolean.TRUE;
				}
				if (!acceptNonQuote)
					throw new ParseException(pos, ERROR_UNEXPECTED_TOKEN, xs);
				handler.primitive(xs);
				return xs;
				// string or NaN
			case 'N':
				xs = readNQString(stop);
				if (!acceptNaN)
					throw new ParseException(pos, ERROR_UNEXPECTED_TOKEN, xs);
				if ("NaN".equals(xs)) {
					handler.primitive(Float.NaN);
					return Float.valueOf(Float.NaN);
				}
				if (!acceptNonQuote)
					throw new ParseException(pos, ERROR_UNEXPECTED_TOKEN, xs);
				handler.primitive(xs);
				return xs;
				// digits
			case '0':
			case '1':
			case '2':
			case '3':
			case '4':
			case '5':
			case '6':
			case '7':
			case '8':
			case '9':
			case '-':
				xo = readNumber(stop);
				handler.primitive(xo);
				return xo;
			default:
				xs = readNQString(stop);
				if (!acceptNonQuote)
					throw new ParseException(pos, ERROR_UNEXPECTED_TOKEN, xs);
				handler.primitive(xs);
				return xs;
			}
		}
	}

	private String readNQString(boolean[] stop) {
		int start = pos;
		skipNQString(stop);
		return in.substring(start, pos).trim();
	}

	private Object readNumber(boolean[] stop) throws ParseException {
		int start = pos;
		// skip first char digit or -
		read();
		skipDigits();
		if (c != '.' && c != 'E' && c != 'e') {
			skipSpace();
			if (c >= 0 && c < MAX_STOP && !stop[c] && c != EOI) {
				// convert string
				skipNQString(stop);
				xs = in.substring(start, pos).trim();
				if (!acceptNonQuote)
					throw new ParseException(pos, ERROR_UNEXPECTED_TOKEN, xs);
				return xs;
			}
			xs = in.substring(start, pos).trim();
			return parseNumber(xs);
		}
		if (c == '.') {
			//
			read();
			skipDigits();
		}
		if (c != 'E' && c != 'e') {
			skipSpace();
			if (c >= 0 && c < MAX_STOP && !stop[c] && c != EOI) {
				// convert string
				skipNQString(stop);
				xs = in.substring(start, pos).trim();
				if (!acceptNonQuote)
					throw new ParseException(pos, ERROR_UNEXPECTED_TOKEN, xs);
				return xs;
			}
			xs = in.substring(start, pos).trim();
			if (!acceptLeadinZero)
				checkLeadinZero();
			if (!useHiPrecisionFloat)
				return Float.parseFloat(xs);
			if (xs.length() > 18) // follow JSjonIJ parssing methode
				return new BigDecimal(xs);
			return Double.parseDouble(xs);
		}
		sb.append('E');
		read();
		if (c == '+' || c == '-' || c >= '0' && c <= '9') {
			sb.append(c);
			read(); // skip first char
			skipDigits();
			skipSpace();
			if (c >= 0 && c < MAX_STOP && !stop[c] && c != EOI) {
				// convert string
				skipNQString(stop);
				xs = in.substring(start, pos).trim();
				if (!acceptNonQuote)
					throw new ParseException(pos, ERROR_UNEXPECTED_TOKEN, xs);
				return xs;
			}
			xs = in.substring(start, pos).trim();
			if (!useHiPrecisionFloat)
				return Float.parseFloat(xs);
			return Double.parseDouble(xs);
		} else {
			skipNQString(stop);
			xs = in.substring(start, pos).trim();
			if (!acceptNonQuote)
				throw new ParseException(pos, ERROR_UNEXPECTED_TOKEN, xs);
			if (!acceptLeadinZero)
				checkLeadinZero();
			return xs;
		}
		// throw new ParseException(pos - 1, ERROR_UNEXPECTED_CHAR, null);
	}

	private Map<String, Object> readObject() throws ParseException, IOException {
		Map<String, Object> obj = this.containerFactory.createObjectContainer();
		if (c != '{')
			throw new RuntimeException("Internal Error");
		handler.startObject();
		boolean needData = false;
		boolean acceptData = true;
		for (;;) {
			read();
			switch (c) {
			case ' ':
			case '\r':
			case '\t':
			case '\n':
				continue;
			case ':':
			case ']':
			case '[':
			case '{':
				throw new ParseException(pos, ERROR_UNEXPECTED_CHAR, c);
			case '}':
				if (needData && !acceptUselessComma)
					throw new ParseException(pos, ERROR_UNEXPECTED_CHAR, (char) c);
				read(); /* unstack */
				handler.endObject();
				return obj;
			case ',':
				if (needData && !acceptUselessComma)
					throw new ParseException(pos, ERROR_UNEXPECTED_CHAR, (char) c);
				acceptData = needData = true;
				continue;
			case '"':
			case '\'':
			default:
				String key;
				int keyStart = pos;
				if (c == '\"' || c == '\'')
					key = readString();
				else {
					key = readNQString(stopKey);
					if (!acceptNonQuote)
						throw new ParseException(pos, ERROR_UNEXPECTED_TOKEN, key);
				}
				if (!acceptData)
					throw new ParseException(pos, ERROR_UNEXPECTED_TOKEN, key);
				handler.startObjectEntry(key);
				while (c != ':' && c != EOI) {
					read();
				}
				if (c == EOI)
					throw new ParseException(pos - 1, ERROR_UNEXPECTED_EOF, null);
				readNoEnd(); /* skip : */
				Object duplicate = obj.put(key, readMain(stopValue));
				if (duplicate != null)
					throw new ParseException(keyStart, ERROR_UNEXPECTED_DUPLICATE_KEY, key);
				handler.endObjectEntry();
				// should loop skipping read step
				if (c == '}') {
					read(); /* unstack */
					handler.endObject();
					return obj;
				}
				if (c == EOI) // Fixed on 18/10/2011 reported by vladimir
					throw new ParseException(pos - 1, ERROR_UNEXPECTED_EOF, null);
				// if c==, continue
				if (c == ',')
					acceptData = needData = true;
				else
					acceptData = needData = false;
				continue;
			}
		}
	}

	private String readString() throws ParseException, IOException {
		if (!acceptSimpleQuote && c == '\'') {
			if (acceptNonQuote)
				return readNQString(stopAll);
			throw new ParseException(pos, ERROR_UNEXPECTED_CHAR, c);
		}
		int tmpP = in.indexOf(c, pos + 1);
		if (tmpP == -1)
			throw new ParseException(in.length(), ERROR_UNEXPECTED_EOF, null);
		xs = in.substring(pos + 1, tmpP);
		if (xs.indexOf('\\') == -1) {
			checkControleChar();
			pos = tmpP;
			read();
			// handler.primitive(tmp);
			return xs;
		}
		sb.clear();// sb.delete(0, sb.length());
		/* assert (c == '\"' || c == '\'') */
		char sep = c;
		for (;;) {
			read();
			switch (c) {
			case EOI:
				throw new ParseException(pos - 1, ERROR_UNEXPECTED_EOF, null);
			case '"':
			case '\'':
				if (sep == c) {
					read();
					return sb.toString();
				}
				sb.append(c);
				break;
			case '\\':
				read();
				switch (c) {
				case 't':
					sb.append('\t');
					break;
				case 'n':
					sb.append('\n');
					break;
				case 'r':
					sb.append('\r');
					break;
				case 'f':
					sb.append('\f');
					break;
				case 'b':
					sb.append('\b');
					break;
				case '\\':
					sb.append('\\');
					break;
				case '/':
					sb.append('/');
					break;
				case '\'':
					sb.append('\'');
					break;
				case '"':
					sb.append('"');
					break;
				case 'u':
					sb.append(readUnicode());
					break;
				default:
					break;
				}
				break;
			case '\0': // end of string
			case (char) 1: // Start of heading
			case (char) 2: // Start of text
			case (char) 3: // End of text
			case (char) 4: // End of transmission
			case (char) 5: // Enquiry
			case (char) 6: // Acknowledge
			case (char) 7: // Bell
			case '\b': // 8: backSpase
			case '\t': // 9: horizontal tab
			case '\n': // 10: new line
			case (char) 11: // Vertical tab
			case '\f': // 12: form feed
			case '\r': // 13: return carriage
			case (char) 14: // Shift Out, alternate character set
			case (char) 15: // Shift In, resume defaultn character set
			case (char) 16: // Data link escape
			case (char) 17: // XON, with XOFF to pause listings;
			case (char) 18: // Device control 2, block-mode flow control
			case (char) 19: // XOFF, with XON is TERM=18 flow control
			case (char) 20: // Device control 4
			case (char) 21: // Negative acknowledge
			case (char) 22: // Synchronous idle
			case (char) 23: // End transmission block, not the same as EOT
			case (char) 24: // Cancel line, MPE echoes !!!
			case (char) 25: // End of medium, Control-Y interrupt
				// case (char) 26: // Substitute
			case (char) 27: // escape
			case (char) 28: // File Separator
			case (char) 29: // Group Separator
			case (char) 30: // Record Separator
			case (char) 31: // Unit Separator
			case (char) 127: // del
				if (ignoreControlChar)
					continue;
				throw new ParseException(pos, ERROR_UNEXPECTED_CHAR, c);
			default:
				sb.append(c);
			}
		}
	}

	private char readUnicode() throws ParseException {
		int value = 0;
		for (int i = 0; i < 4; i++) {
			value = value * 16;
			read();
			if (c >= '0' && c <= '9')
				value += c - '0';
			else if (c >= 'A' && c <= 'F')
				value += (c - 'A') + 10;
			else if (c >= 'a' && c <= 'f')
				value += (c - 'a') + 10;
			else if (c == EOI)
				throw new ParseException(pos, ERROR_UNEXPECTED_EOF, "EOF");
			else
				throw new ParseException(pos, ERROR_UNEXPECTED_UNICODE, c);
		}
		return (char) value;
	}

	private void skipDigits() {
		for (;;) {
			if (c == EOI)
				return;
			if (c < '0' || c > '9')
				return;
			//
			read();
		}
	}

	private void skipNQString(boolean[] stop) {
		for (;;) {
			if (c == EOI)
				return;
			if (c >= 0 && c < MAX_STOP && stop[c])
				return;
			//
			read();
		}
	}

	private void skipSpace() {
		for (;;) {
			if (c == EOI)
				return;
			if (c != ' ' && c != '\r' && c != '\t' && c != '\n')
				return;
			//
			read();
		}
	}
}