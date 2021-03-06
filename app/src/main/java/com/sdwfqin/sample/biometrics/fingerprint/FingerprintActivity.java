package com.sdwfqin.sample.biometrics.fingerprint;

import android.annotation.TargetApi;
import android.app.KeyguardManager;
import android.content.Intent;
import android.hardware.biometrics.BiometricPrompt;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.blankj.utilcode.util.LogUtils;
import com.sdwfqin.sample.MainActivity;
import com.sdwfqin.sample.R;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * 描述：指纹识别
 * <p>
 * 参考：https://blog.csdn.net/love_xiaozhao/article/details/81316145?utm_source=blogxgwz0
 *
 * @author 张钦
 * @date 2018/10/15
 */
public class FingerprintActivity extends AppCompatActivity {

    private static final String DEFAULT_KEY_NAME = "default_key";

    @BindView(R.id.list)
    ListView mList;

    private String[] mTitle = new String[]{
            "指纹测试"};

    KeyStore keyStore;

    /**
     * 管理系统提供的生物识别对话框的类（Android P）
     */
    private BiometricPrompt mBiometricPrompt;

    private Signature mSignature;
    private String mToBeSignedMessage;

    /**
     * 提供取消正在进行的操作的功能
     */
    private CancellationSignal mCancellationSignal;
    /**
     * 识别回调（Android P）
     */
    private BiometricPrompt.AuthenticationCallback mAuthenticationCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        ButterKnife.bind(this);

        mList.setAdapter(new ArrayAdapter<>(this, R.layout.item_list, R.id.tv_items, mTitle));
        initListener();
    }

    private void initListener() {
        mList.setOnItemClickListener((adapterView, view, i, l) -> {
            switch (i) {
                case 0:
                    if (isSupportFingerprint()) {
                        initFingerprint();
                    }
                    break;
            }
        });
    }

    /**
     * 判断是否支持指纹（先用Android M的方式来判断）
     */
    private boolean isSupportFingerprint() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Toast.makeText(this, "您的系统版本过低，不支持指纹功能", Toast.LENGTH_SHORT).show();
            return false;
        } else {
            KeyguardManager keyguardManager = getSystemService(KeyguardManager.class);
            FingerprintManager fingerprintManager = getSystemService(FingerprintManager.class);

            if (!fingerprintManager.isHardwareDetected()) {
                Toast.makeText(this, "您的手机不支持指纹功能", Toast.LENGTH_SHORT).show();
                return false;
            } else if (!keyguardManager.isKeyguardSecure()) {
                Toast.makeText(this, "您还未设置锁屏，请先设置锁屏并添加一个指纹", Toast.LENGTH_SHORT).show();
                return false;
            } else if (!fingerprintManager.hasEnrolledFingerprints()) {
                Toast.makeText(this, "您至少需要在系统设置中添加一个指纹", Toast.LENGTH_SHORT).show();
                return false;
            }
        }
        return true;
    }

    /**
     * 默认弹窗
     */
    private void initFingerprint() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            initFingerprintManager();
        } else {
            initBiometricP();
        }
    }

    /**
     * Android P+ 指纹
     */
    @TargetApi(Build.VERSION_CODES.P)
    private void initBiometricP() {
        mBiometricPrompt = new BiometricPrompt.Builder(this)
                .setTitle("指纹验证")
                .setDescription("描述")
                .setNegativeButton("取消",
                        getMainExecutor(),
                        (dialogInterface, i) -> {
                            LogUtils.i("取消");
                        })
                .build();

        try {
            KeyPair keyPair = generateKeyPair(DEFAULT_KEY_NAME, true);
            // 将密钥对的公钥部分发送到服务器，该公钥将用于认证
            mToBeSignedMessage = new StringBuilder()
                    .append(Base64.encodeToString(keyPair.getPublic().getEncoded(), Base64.URL_SAFE))
                    .append(":")
                    .append(DEFAULT_KEY_NAME)
                    .append(":")
                    // Generated by the server to protect against replay attack
                    .append("12345")
                    .toString();

            mSignature = initSignature(DEFAULT_KEY_NAME);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        mAuthenticationCallback = new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                LogUtils.i("onAuthenticationError " + errString);
            }

            @Override
            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                LogUtils.i("onAuthenticationSucceeded " + result.toString());
                onAuthenticated();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                LogUtils.i("onAuthenticationFailed ");
            }
        };
        if (mCancellationSignal == null) {
            mCancellationSignal = new CancellationSignal();
        }
        mBiometricPrompt.authenticate(new BiometricPrompt.CryptoObject(mSignature), mCancellationSignal, getMainExecutor(), mAuthenticationCallback);
    }

    /**
     * Android M+ 指纹
     */
    @TargetApi(Build.VERSION_CODES.M)
    private void initFingerprintManager() {
        initKey();
        Cipher cipher = initCipher();
        // 指纹识别弹窗
        showFingerPrintDialog(cipher);
    }

    /**
     * 创建密钥
     */
    @TargetApi(Build.VERSION_CODES.M)
    private void initKey() {
        try {
            keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(DEFAULT_KEY_NAME,
                    KeyProperties.PURPOSE_ENCRYPT |
                            KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setUserAuthenticationRequired(true)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7);
            keyGenerator.init(builder.build());
            keyGenerator.generateKey();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 初始化Cipher(加密类，指纹扫描器会使用这个对象来判断认证结果的合法性)
     */
    @TargetApi(Build.VERSION_CODES.M)
    private Cipher initCipher() {
        try {
            SecretKey key = (SecretKey) keyStore.getKey(DEFAULT_KEY_NAME, null);
            Cipher cipher = Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/"
                    + KeyProperties.BLOCK_MODE_CBC + "/"
                    + KeyProperties.ENCRYPTION_PADDING_PKCS7);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return cipher;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 显示指纹识别弹窗
     */
    private void showFingerPrintDialog(Cipher cipher) {
        FingerprintDialogFragment fragment = new FingerprintDialogFragment();
        fragment.setCipher(cipher);
        fragment.setOnClickListener(this::onAuthenticated);
        fragment.show(getSupportFragmentManager(), "fingerprint");
    }

    /**
     * 认证成功
     */
    public void onAuthenticated() {
        Toast.makeText(this, "指纹认证成功", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    /**
     * Generate NIST P-256 EC Key pair for signing and verification
     *
     * @param keyName
     * @param invalidatedByBiometricEnrollment
     * @return
     * @throws Exception
     */
    @TargetApi(Build.VERSION_CODES.P)
    private KeyPair generateKeyPair(String keyName, boolean invalidatedByBiometricEnrollment) throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");

        KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(keyName,
                KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256,
                        KeyProperties.DIGEST_SHA384,
                        KeyProperties.DIGEST_SHA512)
                // Require the user to authenticate with a biometric to authorize every use of the key
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(invalidatedByBiometricEnrollment);

        keyPairGenerator.initialize(builder.build());

        return keyPairGenerator.generateKeyPair();
    }

    @Nullable
    private KeyPair getKeyPair(String keyName) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(keyName)) {
            // Get public key
            PublicKey publicKey = keyStore.getCertificate(keyName).getPublicKey();
            // Get private key
            PrivateKey privateKey = (PrivateKey) keyStore.getKey(keyName, null);
            // Return a key pair
            return new KeyPair(publicKey, privateKey);
        }
        return null;
    }

    @Nullable
    private Signature initSignature(String keyName) throws Exception {
        KeyPair keyPair = getKeyPair(keyName);

        if (keyPair != null) {
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initSign(keyPair.getPrivate());
            return signature;
        }
        return null;
    }
}
