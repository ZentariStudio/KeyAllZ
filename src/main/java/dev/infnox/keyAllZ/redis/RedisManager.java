package dev.infnox.keyAllZ.redis;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;
import redis.clients.jedis.params.SetParams;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RedisManager implements Closeable {

    private final JedisPool pool;
    private final Logger logger;
    private volatile boolean closed = false;

    private Thread subscriberThread;
    private JedisPubSub pubSub;

    public RedisManager(String host, int port, String password, int database, int maxTotal, int maxIdle, int minIdle, Logger logger) {
        this.logger = logger;

        JedisPoolConfig cfg = new JedisPoolConfig();
        cfg.setMaxTotal(maxTotal > 0 ? maxTotal : 16);
        cfg.setMaxIdle(maxIdle > 0 ? maxIdle : 8);
        cfg.setMinIdle(minIdle > 0 ? minIdle : 2);
        
        cfg.setTestOnBorrow(false);
        cfg.setTestOnReturn(false);
        cfg.setTestWhileIdle(true);
        cfg.setBlockWhenExhausted(true);
        cfg.setMaxWait(java.time.Duration.ofMillis(2000));

        String pass = (password != null && !password.isBlank()) ? password : null;
        this.pool = new JedisPool(cfg, host, port, 2000, pass, database);
    }

    public boolean ping() {
        try (Jedis j = pool.getResource()) {
            return "PONG".equals(j.ping());
        } catch (Exception e) {
            return false;
        }
    }

    public void publish(String channel, String message) {
        if (closed) return;
        try (Jedis j = pool.getResource()) {
            j.publish(channel, message);
        } catch (Exception e) {
            logger.log(Level.WARNING, "[KeyAllZ-Redis] publish failed: " + e.getMessage());
        }
    }

    public List<Boolean> setNxBatch(List<String> keys, String value, int ttlSeconds) {
        if (keys.isEmpty()) return Collections.emptyList();
        try (Jedis j = pool.getResource()) {
            Pipeline pipe = j.pipelined();
            SetParams params = SetParams.setParams().nx().ex(ttlSeconds);
            List<Response<String>> responses = new ArrayList<>(keys.size());
            for (String key : keys) {
                responses.add(pipe.set(key, value, params));
            }
            pipe.sync();
            List<Boolean> results = new ArrayList<>(keys.size());
            for (Response<String> r : responses) {
                results.add("OK".equals(r.get()));
            }
            return results;
        } catch (Exception e) {
            logger.log(Level.WARNING, "[KeyAllZ-Redis] setNxBatch failed: " + e.getMessage());
            return Collections.nCopies(keys.size(), false);
        }
    }

    public boolean setNx(String key, String value, int ttlSeconds) {
        try (Jedis j = pool.getResource()) {
            return "OK".equals(j.set(key, value, SetParams.setParams().nx().ex(ttlSeconds)));
        } catch (Exception e) {
            logger.log(Level.WARNING, "[KeyAllZ-Redis] setNx failed: " + e.getMessage());
            return false;
        }
    }

    public boolean renew(String key, String expectedValue, int ttlSeconds) {
        try (Jedis j = pool.getResource()) {
            String script = "if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('expire',KEYS[1],ARGV[2]) else return 0 end";
            Object r = j.eval(script, 1, key, expectedValue, String.valueOf(ttlSeconds));
            return Long.valueOf(1L).equals(r);
        } catch (Exception e) {
            logger.log(Level.WARNING, "[KeyAllZ-Redis] renew failed: " + e.getMessage());
            return false;
        }
    }

    public void hset(String key, Map<String, String> fields) {
        try (Jedis j = pool.getResource()) {
            j.hset(key, fields);
        } catch (Exception e) {
            logger.log(Level.WARNING, "[KeyAllZ-Redis] hset failed: " + e.getMessage());
        }
    }

    public Map<String, String> hgetAll(String key) {
        try (Jedis j = pool.getResource()) {
            return j.hgetAll(key);
        } catch (Exception e) {
            logger.log(Level.WARNING, "[KeyAllZ-Redis] hgetAll failed: " + e.getMessage());
            return Map.of();
        }
    }

    public Set<String> smembers(String key) {
        try (Jedis j = pool.getResource()) {
            return j.smembers(key);
        } catch (Exception e) {
            logger.log(Level.WARNING, "[KeyAllZ-Redis] smembers failed: " + e.getMessage());
            return Set.of();
        }
    }

    public void sadd(String key, String member) {
        try (Jedis j = pool.getResource()) {
            j.sadd(key, member);
        } catch (Exception e) {
            logger.log(Level.WARNING, "[KeyAllZ-Redis] sadd failed: " + e.getMessage());
        }
    }

    public void srem(String key, String member) {
        try (Jedis j = pool.getResource()) {
            j.srem(key, member);
        } catch (Exception e) {
            logger.log(Level.WARNING, "[KeyAllZ-Redis] srem failed: " + e.getMessage());
        }
    }

    public void del(String key) {
        try (Jedis j = pool.getResource()) {
            j.del(key);
        } catch (Exception e) {
            logger.log(Level.WARNING, "[KeyAllZ-Redis] del failed: " + e.getMessage());
        }
    }

    public void subscribe(String channel, Consumer<String> handler) {
        pubSub = new JedisPubSub() {
            @Override
            public void onMessage(String ch, String msg) {
                handler.accept(msg);
            }
        };

        subscriberThread = new Thread(() -> {
            while (!closed) {
                try (Jedis j = pool.getResource()) {
                    j.subscribe(pubSub, channel);
                } catch (Exception e) {
                    if (!closed) {
                        logger.log(Level.WARNING, "[KeyAllZ-Redis] subscriber disconnected, retrying in 5s...");
                        try { Thread.sleep(5000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                    }
                }
            }
        }, "KeyAllZ-Redis-Sub");
        subscriberThread.setDaemon(true);
        subscriberThread.start();
    }

    @Override
    public void close() {
        closed = true;
        try {
            if (pubSub != null && pubSub.isSubscribed()) pubSub.unsubscribe();
        } catch (Exception ignored) {}
        if (subscriberThread != null) subscriberThread.interrupt();
        pool.close();
    }
}
