package pt.servimatch.platform.idempotency;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Wrapper que permite ler o corpo do pedido mais do que uma vez: o
 * {@link IdempotencyFilter} lê-o uma vez para calcular o hash de deteção de
 * reutilização de chave, e o resto da cadeia (conversores JSON do
 * controller) precisa de o voltar a ler na íntegra.
 */
final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] body;

    CachedBodyHttpServletRequest(HttpServletRequest request, byte[] body) {
        super(request);
        this.body = body;
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return byteArrayInputStream.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                // Not needed for the synchronous, blocking use case in this filter chain.
            }

            @Override
            public int read() {
                return byteArrayInputStream.read();
            }
        };
    }

    @Override
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(getInputStream(), getCharacterEncodingOrDefault()));
    }

    private String getCharacterEncodingOrDefault() {
        return getCharacterEncoding() != null ? getCharacterEncoding() : StandardCharsets.UTF_8.name();
    }
}
