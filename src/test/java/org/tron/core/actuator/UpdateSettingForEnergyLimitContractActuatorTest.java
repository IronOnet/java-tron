package org.tron.core.actuator;

import static junit.framework.TestCase.fail;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.File;
import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.tron.common.application.TronApplicationContext;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.FileUtil;
import org.tron.common.utils.StringUtil;
import org.tron.core.Constant;
import org.tron.core.Wallet;
import org.tron.core.capsule.AccountCapsule;
import org.tron.core.capsule.ContractCapsule;
import org.tron.core.capsule.TransactionResultCapsule;
import org.tron.core.config.DefaultConfig;
import org.tron.core.config.args.Args;
import org.tron.core.db.Manager;
import org.tron.core.exception.ContractExeException;
import org.tron.core.exception.ContractValidateException;
import org.tron.core.exception.TronException;
import org.tron.protos.Contract;
import org.tron.protos.Protocol;


@Slf4j
public class UpdateSettingForEnergyLimitContractActuatorTest {

  private static TronApplicationContext context;
  private static Manager dbManager;
  private static final String dbPath = "output_updatesettingforenergylimitcontract_test";
  private static final String OWNER_ADDRESS =
      Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049abc";
  private static final String OWNER_ADDRESS_ACCOUNT_NAME = "test_account";
  private static final String SECOND_ACCOUNT_ADDRESS =
      Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d427122222";
  private static final String OWNER_ADDRESS_NOTEXIST =
      Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1abc";
  private static final String OWNER_ADDRESS_INVALID = "aaaa";
  private static final String SMART_CONTRACT_NAME = "smart_contarct";
  private static final String CONTRACT_ADDRESS = "111111";
  private static final String NO_EXIST_CONTRACT_ADDRESS = "2222222";
  private static final long SOURCE_ENERGY_LIMIT = 10L;
  private static final long TARGET_ENERGY_LIMIT = 30L;
  private static final long INVALID_ENERGY_LIMIT = -200L;

  static {
    Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
    context = new TronApplicationContext(DefaultConfig.class);
  }

  /**
   * Init data.
   */
  @BeforeClass
  public static void init() {
    dbManager = context.getBean(Manager.class);
  }

  /**
   * create temp Capsule test need.
   */
  @Before
  public void createCapsule() {
    // address in accountStore and the owner of contract
    AccountCapsule accountCapsule =
        new AccountCapsule(
            ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
            ByteString.copyFromUtf8(OWNER_ADDRESS_ACCOUNT_NAME),
            Protocol.AccountType.Normal);
    dbManager.getAccountStore().put(ByteArray.fromHexString(OWNER_ADDRESS), accountCapsule);

    // smartContract in contractStore
    Protocol.SmartContract.Builder builder = Protocol.SmartContract.newBuilder();
    builder.setName(SMART_CONTRACT_NAME);
    builder.setOriginAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)));
    builder.setContractAddress(ByteString.copyFrom(ByteArray.fromHexString(CONTRACT_ADDRESS)));
    builder.setEnergyLimit(SOURCE_ENERGY_LIMIT);
    dbManager.getContractStore().put(
        ByteArray.fromHexString(CONTRACT_ADDRESS),
        new ContractCapsule(builder.build()));

    // address in accountStore not the owner of contract
    AccountCapsule secondAccount =
        new AccountCapsule(
            ByteString.copyFrom(ByteArray.fromHexString(SECOND_ACCOUNT_ADDRESS)),
            ByteString.copyFromUtf8(OWNER_ADDRESS_ACCOUNT_NAME),
            Protocol.AccountType.Normal);
    dbManager.getAccountStore().put(ByteArray.fromHexString(SECOND_ACCOUNT_ADDRESS), secondAccount);

    // address does not exist in accountStore
    dbManager.getAccountStore().delete(ByteArray.fromHexString(OWNER_ADDRESS_NOTEXIST));
  }

  /**
   * Release resources.
   */
  @AfterClass
  public static void destroy() {
    Args.clearParam();
    context.destroy();
    if (FileUtil.deleteDir(new File(dbPath))) {
      logger.info("Release resources successful.");
    } else {
      logger.info("Release resources failure.");
    }
  }

  private Any getContract(String accountAddress, String contractAddress, long energyLimit) {
    return Any.pack(
        Contract.UpdateSettingForEnergyLimitContract.newBuilder()
            .setOwnerAddress(StringUtil.hexString2ByteString(accountAddress))
            .setContractAddress(StringUtil.hexString2ByteString(contractAddress))
            .setEnergyLimit(energyLimit).build());
  }

  @Test
  public void successUpdateSettingForEnergyLimitContract() throws InvalidProtocolBufferException {
    UpdateSettingForEnergyLimitContractActuator actuator =
        new UpdateSettingForEnergyLimitContractActuator(
            getContract(OWNER_ADDRESS, CONTRACT_ADDRESS, TARGET_ENERGY_LIMIT), dbManager);

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);

      // assert result state and energy_limit
      Assert.assertEquals(OWNER_ADDRESS,
          ByteArray.toHexString(actuator.getOwnerAddress().toByteArray()));
      Assert.assertEquals(ret.getInstance().getRet(), Protocol.Transaction.Result.code.SUCESS);
      Assert.assertEquals(
          dbManager.getContractStore().get(ByteArray.fromHexString(CONTRACT_ADDRESS))
              .getEnergyLimit(), TARGET_ENERGY_LIMIT);
    } catch (ContractValidateException | ContractExeException e) {
      Assert.fail(e.getMessage());
    }
  }

  @Test
  public void invalidAddress() {
    UpdateSettingForEnergyLimitContractActuator actuator =
        new UpdateSettingForEnergyLimitContractActuator(
            getContract(OWNER_ADDRESS_INVALID, CONTRACT_ADDRESS, TARGET_ENERGY_LIMIT), dbManager);

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);

      fail("Invalid address");
    } catch (TronException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Invalid address", e.getMessage());
    }
  }

  @Test
  public void noExistAccount() {
    UpdateSettingForEnergyLimitContractActuator actuator =
        new UpdateSettingForEnergyLimitContractActuator(
            getContract(OWNER_ADDRESS_NOTEXIST, CONTRACT_ADDRESS, TARGET_ENERGY_LIMIT), dbManager);

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);

      fail("Account[" + OWNER_ADDRESS_NOTEXIST + "] not exists");
    } catch (TronException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Account[" + OWNER_ADDRESS_NOTEXIST + "] not exists", e.getMessage());
    }
  }

  @Test
  public void invalidResourceEnergyLimit() {
    UpdateSettingForEnergyLimitContractActuator actuator =
        new UpdateSettingForEnergyLimitContractActuator(
            getContract(OWNER_ADDRESS, CONTRACT_ADDRESS, INVALID_ENERGY_LIMIT), dbManager);

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);

      fail("energy limit less than 0");
    } catch (TronException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("energy limit not less than 0", e.getMessage());
    }
  }

  @Test
  public void noExistContract() {
    UpdateSettingForEnergyLimitContractActuator actuator =
        new UpdateSettingForEnergyLimitContractActuator(
            getContract(OWNER_ADDRESS, NO_EXIST_CONTRACT_ADDRESS, TARGET_ENERGY_LIMIT), dbManager);

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);

      fail("Contract not exists");
    } catch (TronException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Contract not exists", e.getMessage());
    }
  }

  @Test
  public void callerNotContractOwner() {
    UpdateSettingForEnergyLimitContractActuator actuator =
        new UpdateSettingForEnergyLimitContractActuator(
            getContract(SECOND_ACCOUNT_ADDRESS, CONTRACT_ADDRESS, TARGET_ENERGY_LIMIT), dbManager);

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);

      fail("Account[" + SECOND_ACCOUNT_ADDRESS + "] is not the owner of the contract");
    } catch (TronException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals(
          "Account[" + SECOND_ACCOUNT_ADDRESS + "] is not the owner of the contract",
          e.getMessage());
    }
  }

  @Test
  public void twiceUpdateSettingForEnergyLimitContract() throws InvalidProtocolBufferException {
    UpdateSettingForEnergyLimitContractActuator actuator =
        new UpdateSettingForEnergyLimitContractActuator(
            getContract(OWNER_ADDRESS, CONTRACT_ADDRESS, TARGET_ENERGY_LIMIT), dbManager);

    UpdateSettingForEnergyLimitContractActuator secondActuator =
        new UpdateSettingForEnergyLimitContractActuator(
            getContract(OWNER_ADDRESS, CONTRACT_ADDRESS, 90L), dbManager);

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      // first
      actuator.validate();
      actuator.execute(ret);

      Assert.assertEquals(OWNER_ADDRESS,
          ByteArray.toHexString(actuator.getOwnerAddress().toByteArray()));
      Assert.assertEquals(ret.getInstance().getRet(), Protocol.Transaction.Result.code.SUCESS);
      Assert.assertEquals(
          dbManager.getContractStore().get(ByteArray.fromHexString(CONTRACT_ADDRESS))
              .getEnergyLimit(), TARGET_ENERGY_LIMIT);

      // second
      secondActuator.validate();
      secondActuator.execute(ret);

      Assert.assertEquals(ret.getInstance().getRet(), Protocol.Transaction.Result.code.SUCESS);
      Assert.assertEquals(
          dbManager.getContractStore().get(ByteArray.fromHexString(CONTRACT_ADDRESS))
              .getEnergyLimit(), 90L);
    } catch (ContractValidateException | ContractExeException e) {
      Assert.fail(e.getMessage());
    }
  }

}
