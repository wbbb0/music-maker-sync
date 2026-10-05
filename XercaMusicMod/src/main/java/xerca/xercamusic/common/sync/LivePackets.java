package xerca.xercamusic.common.sync;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import xerca.xercamusic.common.Mod;

public final class LivePackets {
    private LivePackets() {}
    public record Note(long time, int key, int velocity) {}
    public record Batch(long session, int sequence, int instrument, BlockPos block, List<Note> notes, boolean end) implements CustomPacketPayload {
        public static final Type<Batch> TYPE = new Type<>(Mod.id("live_batch"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Batch> CODEC = StreamCodec.of((b,p) -> {
            b.writeLong(p.session); b.writeVarInt(p.sequence); b.writeVarInt(p.instrument);
            b.writeNullable(p.block, (buf,pos) -> buf.writeBlockPos(pos));
            b.writeVarInt(p.notes.size());
            for (Note n : p.notes) { b.writeVarLong(n.time); b.writeByte(n.key); b.writeByte(n.velocity); }
            b.writeBoolean(p.end);
        }, b -> {
            long session=b.readLong(); int seq=b.readVarInt(), ins=b.readVarInt(); BlockPos pos=b.readNullable(buf -> buf.readBlockPos());
            int count=b.readVarInt(); if(count<0 || count>32) throw new IllegalArgumentException("live batch size");
            List<Note> notes=new ArrayList<>(count);
            for(int i=0;i<count;i++) notes.add(new Note(b.readVarLong(),b.readUnsignedByte(),b.readUnsignedByte()));
            return new Batch(session,seq,ins,pos,List.copyOf(notes),b.readBoolean());
        });
        @Override public Type<Batch> type(){return TYPE;}
    }
    public record Delivery(UUID player, long anchor, Batch batch, boolean abort) implements CustomPacketPayload {
        public static final Type<Delivery> TYPE=new Type<>(Mod.id("live_delivery"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Delivery> CODEC=StreamCodec.of((b,p)->{
            b.writeUUID(p.player); b.writeLong(p.anchor); Batch.CODEC.encode(b,p.batch); b.writeBoolean(p.abort);
        },b->new Delivery(b.readUUID(),b.readLong(),Batch.CODEC.decode(b),b.readBoolean()));
        @Override public Type<Delivery> type(){return TYPE;}
    }
    public record Probe(long sent) implements CustomPacketPayload {
        public static final Type<Probe> TYPE=new Type<>(Mod.id("clock_probe"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Probe> CODEC=StreamCodec.of((b,p)->b.writeLong(p.sent),b->new Probe(b.readLong()));
        @Override public Type<Probe> type(){return TYPE;}
    }
    public record Clock(long sent,long server) implements CustomPacketPayload {
        public static final Type<Clock> TYPE=new Type<>(Mod.id("clock_reply"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Clock> CODEC=StreamCodec.of((b,p)->{b.writeLong(p.sent);b.writeLong(p.server);},b->new Clock(b.readLong(),b.readLong()));
        @Override public Type<Clock> type(){return TYPE;}
    }
}
