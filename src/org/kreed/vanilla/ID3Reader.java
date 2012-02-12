/*
 * Copyright (C) 2011 Christopher Eby <kreed@kreed.org>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.kreed.vanilla;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;

// Based off of http://polylingualprogrammer.com/java/lesson6/solution.html

public class ID3Reader {
	private static final String[] ENCODINGS = { "US-ASCII", "UTF-16", "UTF-16BE", "UTF-8"};

	// id3 header flags
	private static final int UNSYNC = 1 << 7;
	private static final int EXTENDED_HEADER = 1 << 6;

	// frame flags
	private static final short GROUPING = 1 << 6;
	private static final short COMPRESSED = 1 << 3;
	private static final short ENCRYPTED = 1 << 2;
	private static final short FRAME_UNSYNC = 1 << 1;
	private static final short DATA_LENGTH = 1;

	private static final int TXXX = 0x54585858;

	public static void readID3(Song song) throws IOException
	{
		if (song == null || song.path == null)
			return;

		DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(song.path)));

		if (!isID3(in.readByte(), in.readByte(), in.readByte()))
			return;

		int majorVersion = in.readByte();
		in.skip(1); // revision

		if (majorVersion < 3)
			return;

		int tagsFlags = (in.readByte() & 0xff);
		int tagsSize = readSyncSafeInt(in);
		if ((tagsFlags & EXTENDED_HEADER) != 0)
			in.skip(readSyncSafeInt(in));
		if ((tagsFlags & UNSYNC) != 0) {
			// should perhaps handle unsynchronization, although it should be
			// pretty unlikely that we'll encounter any unsynchronizations in
			// TXXX frames.
		}

		for (;;) {
			byte a = in.readByte();
			byte b = in.readByte();
			byte c = in.readByte();
			byte d = in.readByte();
			if (a == 0 || isMP3Sync(a, b) || isID3(c, b, a))
				return;

			int frame = (a << 24) | (b << 16) | (c << 8) | d;

			int size;
			if (majorVersion <= 3)
				size = in.readInt();
			else
				size = readSyncSafeInt(in);
			if (size > tagsSize)
				return; // bad!
			short frameFlags = in.readShort();

			if ((frameFlags & DATA_LENGTH) != 0) {
				in.skip(4);
				size -= 4;
			}
			if ((frameFlags & GROUPING) != 0) {
				in.skip(1);
				size -= 1;
			}

			if (((frameFlags & COMPRESSED) != 0) ||
				((frameFlags & ENCRYPTED) != 0) ||
				((frameFlags & FRAME_UNSYNC) != 0)) {
				in.skipBytes(size);
				continue;
			}

			if (frame == TXXX) {
				byte encoding = in.readByte();
				String encodingName = ENCODINGS[encoding];
				size -= 1;

				byte[] bytes = new byte[size];
				in.read(bytes);

				int charWidth = encoding == 1 || encoding == 2 ? 2 : 1;
				int descriptionSize = strlen(bytes, charWidth, 0);
				int valueStart = descriptionSize + charWidth;
				int valueSize = strlen(bytes, charWidth, valueStart);

				String description = null;
				String value = null;

				if (descriptionSize > 0)
					description = new String(bytes, 0, descriptionSize, encodingName);
				if (valueSize > 0)
					value = new String(bytes, valueStart, valueSize, encodingName);

				song.processTag(description, value);
			} else {
				in.skipBytes(size);
			}
		}
	}

	public static int strlen(byte[] bytes, int charWidth, int start)
	{
		if (charWidth == 2) {
			for (int i = start, end = bytes.length; i < end; i += 2) {
				if (bytes[i] == 0 && bytes[i + 1] == 0)
					return i - start;
			}
		} else {
			for (int i = start, end = bytes.length; i < end; ++i) {
				if (bytes[i] == 0)
					return i - start;
			}
		}
		return bytes.length - start;
	}

	private static int readSyncSafeInt(DataInputStream in) throws IOException
	{
		int ssi = 0;
		for (int i = 3; i != -1;  --i)
			ssi |= (in.readByte() & 0x7f) << (7 * i);
		return ssi;
	}

	private static boolean isMP3Sync(byte b1, byte b2)
	{
		return b1 == 0xff && b2 >= 0xe0;
	}

	private static boolean isID3(byte a, byte b, byte c)
	{
		return a == 0x49 && b == 0x44 && c == 0x33;
	}
}
