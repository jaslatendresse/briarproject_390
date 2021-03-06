package org.briarproject.bramble.sync;

import org.briarproject.bramble.api.UniqueId;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageFactory;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.util.ByteUtils;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static org.briarproject.bramble.api.sync.MessageId.LABEL;
import static org.briarproject.bramble.api.sync.SyncConstants.MAX_MESSAGE_BODY_LENGTH;
import static org.briarproject.bramble.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;
import static org.briarproject.bramble.api.sync.SyncConstants.PROTOCOL_VERSION;

@Immutable
@NotNullByDefault
class MessageFactoryImpl implements MessageFactory {

	private final CryptoComponent crypto;

	@Inject
	MessageFactoryImpl(CryptoComponent crypto) {
		this.crypto = crypto;
	}

	@Override
	public Message createMessage(GroupId groupId, long timestamp, byte[] body) {
		if (body.length > MAX_MESSAGE_BODY_LENGTH) {
			throw new IllegalArgumentException();
		}

		byte[] groupIdBytes = groupId.getBytes();
		byte[] timeBytes = new byte[ByteUtils.INT_64_BYTES];
		byte[] hash = crypto.hash(LABEL, new byte[] {PROTOCOL_VERSION}, groupIdBytes, timeBytes, body);
		byte[] raw = new byte[MESSAGE_HEADER_LENGTH + body.length];

		ByteUtils.writeUint64(timestamp, timeBytes, 0);
		MessageId id = new MessageId(hash);

		System.arraycopy(groupIdBytes, 0, raw, 0, UniqueId.LENGTH);
		ByteUtils.writeUint64(timestamp, raw, UniqueId.LENGTH);
		System.arraycopy(body, 0, raw, MESSAGE_HEADER_LENGTH, body.length);
		return new Message(id, groupId, timestamp, raw, false);
	}

	@Override
	public Message createMessage(MessageId m, byte[] raw) {
		if (raw.length < MESSAGE_HEADER_LENGTH)
			throw new IllegalArgumentException();
		byte[] groupId = new byte[UniqueId.LENGTH];
		System.arraycopy(raw, 0, groupId, 0, UniqueId.LENGTH);
		long timestamp = ByteUtils.readUint64(raw, UniqueId.LENGTH);
		return new Message(m, new GroupId(groupId), timestamp, raw, false);
	}

	@Override
	public Message createMessage(MessageId m, byte[] raw, Boolean pinned) {
		if (raw.length < MESSAGE_HEADER_LENGTH)
			throw new IllegalArgumentException();
		byte[] groupId = new byte[UniqueId.LENGTH];
		System.arraycopy(raw, 0, groupId, 0, UniqueId.LENGTH);
		long timestamp = ByteUtils.readUint64(raw, UniqueId.LENGTH);
		return new Message(m, new GroupId(groupId), timestamp, raw, pinned);
	}
}
