package big.pig.server;

import big.pig.common.proto.PbContact;
import big.pig.server.handler.BaseServerHandler;
import big.pig.server.handler.MataServerHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import io.netty.handler.stream.ChunkedWriteHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Server01 {
    private static final Logger logger = LoggerFactory.getLogger(Server01.class);

    private final ServiceThreadFactory serviceThreadFactory;

    @Autowired
    public Server01(ServiceThreadFactory serviceThreadFactory) {
        this.serviceThreadFactory = serviceThreadFactory;
    }

    @Value("${tcp.port}")
    private int tPort;

    @Value("${http.port}")
    private int hPort;

    public void start() throws Exception {

        EventLoopGroup bossGroup = new NioEventLoopGroup(1);//设置接收线程池
        EventLoopGroup workerGroup = new NioEventLoopGroup(2);//设置工作线程池(默认系统cpu核数*2)
        final EventLoopGroup serviceGroup = new NioEventLoopGroup(2,serviceThreadFactory);//设置业务程池(默认系统cpu核数*2)

        try {

            ServerBootstrap tcpBootstrap = new ServerBootstrap();
            ServerBootstrap httpBootstrap = new ServerBootstrap();

            httpBootstrap.group(bossGroup,workerGroup)
                .channel(NioServerSocketChannel.class)
                //---------------option设置--------------------------
                .option(ChannelOption.SO_BACKLOG, 128) //临时等待处理连接队列长度
                .option(ChannelOption.SO_REUSEADDR,true)//端口重复绑定
                .option(ChannelOption.SO_RCVBUF, 10*1024) //接收缓冲区长度
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)//内存池分配方案
                .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)//内存池分配方案
                //-----------------------------------------------------------------------------------
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast("http-response", new HttpResponseEncoder());//Http响应编码器
                        ch.pipeline().addLast("http-request", new HttpRequestDecoder());//Http请求解码器
                        ch.pipeline().addLast("http-aggregator", new HttpObjectAggregator(65535));//http对象聚合器，最多接收65535数组长度字节流
                        ch.pipeline().addLast("http-chunk", new ChunkedWriteHandler());//分块发送
                        ch.pipeline().addLast("frameDecoder",new ProtobufVarint32FrameDecoder());//包头解码器
                        ch.pipeline().addLast("protobufDecoder", new ProtobufDecoder(PbContact.Request.getDefaultInstance()));//配置protoBuf解码处理器
                        ch.pipeline().addLast("frameEncoder", new ProtobufVarint32LengthFieldPrepender());// 用于在序列化的字节数组前加上一个简单的包头，只包含序列化的字节长度,包头编码器。
                        ch.pipeline().addLast("protobufEncoder", new ProtobufEncoder());//配置Protobuf编码器
                        ch.pipeline().addLast(serviceGroup,new BaseServerHandler());//http基础服务
                    }
                });

            tcpBootstrap.group(bossGroup,workerGroup)
                .channel(NioServerSocketChannel.class)
                //---------------option设置--------------------------
                .option(ChannelOption.SO_BACKLOG, 128) //临时等待处理连接队列长度
                .option(ChannelOption.SO_REUSEADDR,true)//端口重复绑定
                .option(ChannelOption.SO_RCVBUF, 10*1024) //接收缓冲区长度
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)//内存池分配方案
                .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)//内存池分配方案
                //-----------------------------------------------------------------------------------
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast("mataDataHandler",new MataServerHandler());//原始数据拦截
                        ch.pipeline().addLast("frameDecoder",new ProtobufVarint32FrameDecoder());//包头解码器
                        ch.pipeline().addLast("protobufDecoder", new ProtobufDecoder(PbContact.Request.getDefaultInstance()));//配置protoBuf解码处理器
                        ch.pipeline().addLast("frameEncoder", new ProtobufVarint32LengthFieldPrepender());// 用于在序列化的字节数组前加上一个简单的包头，只包含序列化的字节长度,包头编码器。
                        ch.pipeline().addLast("protobufEncoder", new ProtobufEncoder());//配置Protobuf编码器
                        ch.pipeline().addLast(serviceGroup,new BaseServerHandler());//tcp基础服务
                    }
                });

            ChannelFuture tcp = tcpBootstrap.bind(tPort).sync();
            ChannelFuture http = httpBootstrap.bind(hPort).sync();

            tcp.channel().closeFuture().sync();
            http.channel().closeFuture().sync();

        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
            serviceGroup.shutdownGracefully();
        }
    }

}
