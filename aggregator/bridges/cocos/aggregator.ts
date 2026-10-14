import { native } from "cc";

const CLASS = "com/aggregator/android/AggregatorBridge";

/** 渠道包启动时由 Android 宿主调用 AggregatorBridge.start(activity)。脚本只调下面这些方法。 */
export function login() {
  native.reflection.callStaticMethod(CLASS, "login", "()V");
}

export function submitRole(
  type: string,
  roleId: string,
  roleName: string,
  roleLevel: number,
  serverId: string,
  serverName: string,
) {
  native.reflection.callStaticMethod(
    CLASS,
    "submitRole",
    "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ILjava/lang/String;Ljava/lang/String;)V",
    type,
    roleId,
    roleName,
    roleLevel,
    serverId,
    serverName,
  );
}

export function pay(orderId: string, productId: string, gameOrderId: string, channelUserId: string, roleId: string) {
  native.reflection.callStaticMethod(
    CLASS,
    "pay",
    "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
    orderId,
    productId,
    gameOrderId,
    channelUserId,
    roleId,
  );
}

export function loadAd(adType: string, placementId: string) {
  native.reflection.callStaticMethod(CLASS, "loadAd", "(Ljava/lang/String;Ljava/lang/String;)V", adType, placementId);
}

export function showAd(adType: string, placementId: string) {
  native.reflection.callStaticMethod(CLASS, "showAd", "(Ljava/lang/String;Ljava/lang/String;)V", adType, placementId);
}

export function poll(): string {
  return native.reflection.callStaticMethod(CLASS, "poll", "()Ljava/lang/String;") || "";
}
