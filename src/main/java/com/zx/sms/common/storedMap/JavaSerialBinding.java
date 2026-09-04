package com.zx.sms.common.storedMap;

import java.io.Serializable;

import com.sleepycat.bind.ByteArrayBinding;
import com.sleepycat.bind.EntryBinding;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.util.RuntimeExceptionWrapper;
import com.zx.sms.common.util.AttachmentSerializer;

public class JavaSerialBinding<E extends Serializable> implements EntryBinding<E> {
	private final static byte[] ZERO_LENGTH_BYTE_ARRAY = new byte[0];
	private ByteArrayBinding bb = new ByteArrayBinding();

		@Override
	public E entryToObject(DatabaseEntry entry) {
		byte[] data = bb.entryToObject(entry);
		if(data ==null || data.length ==0) return null;
		return (E)AttachmentSerializer.read(data);
	}

	@Override
	public void objectToEntry(E object, DatabaseEntry entry) {
		if(object==null){
			bb.objectToEntry(ZERO_LENGTH_BYTE_ARRAY, entry);
		}else{
			try{
				bb.objectToEntry(AttachmentSerializer.write(object), entry);
			}catch(Exception ex){
				throw RuntimeExceptionWrapper.wrapIfNeeded(ex);
			}
		}
	}
}
