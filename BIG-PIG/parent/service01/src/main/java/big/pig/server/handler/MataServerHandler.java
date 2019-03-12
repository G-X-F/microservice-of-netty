package big.pig.server.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 用于拦截原始的二进制数据检查、调试
 */
public class MataServerHandler extends ChannelInboundHandlerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(MataServerHandler.class);

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
            ByteBuf buf = 	(ByteBuf)msg;
            byte[] bytes = new byte[buf.readableBytes()];//计算读取长度
            buf.markReaderIndex();//标记读取起始点
            buf.readBytes(bytes);//读取BytBuf数据
            //System.out.println("<===" + Arrays.toString(bytes));
            buf.resetReaderIndex();//重置到读取起始点
            ctx.fireChannelRead(msg); //向下传递
        }catch (Exception e){
            logger.error("",e);
        }
    }
}
