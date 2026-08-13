**青岛地铁ITP与APP接口规范**

1. 总体说明
   1. 通讯协议

接口通过HTTP协议POST请求方式实现.

签名原始串按以下方式组装成字符串：

1. 除sign字段外，所有参数按照字段名的ascii码从小到大排序后使用QueryString的格式（即key1=value1&key2=value2…）拼接而成，空值不传递，不参与签名组串；
2. 所有参数是指通信过程中实际出现的所有非空参数，即使是接口中无描述的字段，也需要参与签名组串；
3. 签名原始串中，sign 字段进行URL Encode；
4. ITP平台返回的应答或通知消息可能会由于升级增加参数，请验证应答签名时注意允许这种情况。
   1. 请求公共参数

请求公共参数详见表1。

1. 请求公共参数列表

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| providerId | string | 商户编码  01:青岛地铁APP  02:TVM  03:BOM  04:AGM  05:ACC  06:ITP  07:STT  其他预留 |
| charset | string | 入参字符集：UTF-8 |
| format | string | 数据格式：json |
| timestamp | string | 请求时间，格式如：  YYYYMMDDHHMMSS |
| deviceId | string | 设备编码 |
| signType | string | 00：不签名  01：sha1withrsa  02：MD5 |
| sign | string | 签名值 |
| bizData | Json | 业务数据 |

* 1. 行业数据定义
  2. 错误代码

错误代码详见表2。

1. 错误代码列表

|  |  |
| --- | --- |
| 定义 | 含义 |
| 0000 | 成功 |
| 9999 | 失败 |
| 9001 | 系统内部错误 |
| 8001 | 无效的参数 |
| 8002 | 已发卡 |
| 8003 | 暂无卡数据资源 |
| 8004 | 没有账号卡片数据 |
| 8005 | 您已进入黑名单请联系客服 |
| 8006 | 账号和卡号不匹配 |
| 8007 | 服务提供商不可用 |
| 8008 | 解约审核中 |
| 8009 | ITP无可用CA证书 |
| 8010 | 请勿重复签约 |
| 8011 | 用户未签约 |
| 8012 | 解除签约 |
| 8013 | 不允许解约默认支付渠道 |
| 8014 | 无效的签约数据 |
| 8021 | 请勿重复添加支付渠道 |
| 8180 | 该订单不能退款 |
| 8181 | 订单已支付成功，请勿重复支付 |
| 8182 | 订单已关闭，请重新创建订单 |
| 8183 | 订单无法激活 |
| 8184 | 订单已激活 |
| 8300 | BOM处理交易，请联系客服 |
| 8301 | 码状态正常，无需更新，请正常刷码 |
| 8302 | 补出站成功，请刷码进站 |
| 8303 | 补进站成功，请刷码出站 |
| 8304 | 无法自助补进出站，超过限制次数 |
| 8311 | BOM返回值状态异常 |
| 8312 | BOM返回值状态异常 |
| 8401 | AGM返回值状态异常 |
| 8402 | AGM返回值状态异常 |
| 8501 | ACC通讯异常 |
| 8502 | ACC返回值状态异常 |

* 1. 支付通道编码

支付通道编码详见表3。

1. 支付通道编码列表

|  |  |
| --- | --- |
| 定义 | 含义 |
| 03 | 支付宝支付 |
| 04 | 微信支付 |
| 05 | 银联支付 |
| 06 | 建行龙支付 |
| 07 | 银联云闪付 |
| 其他 | 预留 |

* 1. 卡类型编码

卡类型编码详见表4。

1. 卡类型编码列表

|  |  |
| --- | --- |
| 定义 | 含义 |
| 02 | 二维码后付费单程票 |
| 03 | HCE后付费单程票 |
| 其他 | 预留 |

1. 业务约束
   1. 后付费二维码业务
2. 请求开户

请求开户流程详见图1。

![](data:image/x-emf;base64...)

1. 请求开户流程

流程说明：

* 1. 地铁APP调用服务器接口请求调用SDK的请求参数。请求调用SDK的请求参数如果已经签约，ITP返回8009，客户端直接调用开户接口。
  2. 服务器应答调用SDK的应答信息。
  3. 地铁APP调用支付通道的SDK完成实名认证和后付费签约。
  4. 支付通道把用户实名（姓名、身份证号等）信息异步回调给ITP平台。
  5. 支付通道把签约信息异步回调给ITP平台。
  6. 乘客通过APP\_SERVER发送开户请求到ITP平台。
  7. ITP平台接收到开卡请求信息后。

如请求参数（基础参数或签名）验证失败,返回retCode：8001；

如请求的合作伙伴验证失败，则返回retCode:8007；

如已注册用户，返回retCode:8002；

如果用户状态为解约审核中，则返回retCode:8008；

如无可用卡资源则返回：8003；

用户信息和卡片信息关联生成用户相关基础信息，完成后返回retCode:0000；

业务处理过程中出现异常时，则返回retCode:9999。

* 1. ITP把开户结果同步返回给APP\_SERVER
  2. APP\_SERVER通知用户开户结

1. 请求同步密钥

请求同步密钥流程详见图54。

![](data:image/x-emf;base64...)

1. 请求同步密钥

流程说明：

* 1. 客户端开户后需主动发起同步密钥请求，并定期维护密钥数据，如密钥过期后需再次向APP\_SERVER发送同步密钥请求。
  2. APP\_SERVER将密钥同步请求转发给ITP平台。
  3. ITP平台验证请求参数，如基础参数或签名验证失败,返回retCode：8001。
  4. ITP平台把用户逻辑卡号、用户ID、证件有效期等信息拼装发送给ACC平台。
  5. ACC把密钥信息（KEK保护的用户私钥、用户公钥XY+公钥证书签名、CA\_IDX）返回给ITP。
  6. ITP把密钥信息用APP\_SERVER和 ITP平台约定的KEK加密 转发给APP\_SERVER。
  7. APP\_SERVER把密钥信息安全下发给地铁APP。
  8. 地铁客户端保存密钥数据。

1. 请求行业数据

请求行业数据流程详见图55。

![](data:image/x-emf;base64...)

1. 请求行业数据流程

流程说明：

* 1. 乘客从地铁APP端发起获取二维码请求到APP\_SERVER。
  2. APP\_SERVER发送获取行业数据请求到ITP平台。
  3. ITP接收到请求后。

如请求参数（如基础参数或签名）验证失败,则返回retCode：8001；

如请求的合作伙伴验证失败，则返回retCode:8007；

如果验证是未注册用户，则返回retCode:8004；

如果用户状态为解约审核中，则返回retCode:8008；

如果用户卡号是否与请求参数不一致，则返回retCode:8006；

服务封装二维码明文串，并对其进行MD5算法，得到行业数据(MD5)；

开卡业务处理完成后返回retCode:0000；

如业务处理过程中出现异常，则返回retCode:9999；

ITP服务将行业数据(MD5)传递给ACC服务；

ACC服务对行业数据(MD5)进行加密，加密后结果返回给ITP服务。

* 1. ITP将行业数据返回给APP\_SERVER。

1. 请求自助补站

请求自助补站流程详见图4。

![](data:image/x-emf;base64...)

1. 请求自助补站流程

流程说明：

* 1. APP\_SERVER发送补进/出站请求到ITP平台；
  2. ITP平台接收到请求后；

如请求参数（如基础参数、合作伙伴信息及签名）验证失败，则返回retCode：8001；

如果验证用户是未注册用户，则返回retCode:8004；

如验证用户状态为解约审核中，则返回retCode:8008；

如验证用户卡号是否与请求的卡号参数不一致，则返回retCode:8006；

如BOM更新后的票卡无法自助更新，则返回retCode:8300；

票卡状态正常，可正常进出站，则返回retCode:8301；

补出站成功，则返回retCode:8302；

补进站成功，则返回retCode:8303；

一天内自助补站次数达到上限，则返回retCode:8304；

补进/出站业务正常处理完成后返回retCode:0000；

业务处理过程中出现异常时，则返回retCode:9999；

* 1. ITP将补进/出站结果通知给APP\_SERVER；
  2. ITP将变更后的行业数据异步推送给 APP\_SERVER。

1. 请求查询交易记录

请求查询交易记录流程详见图5。

![](data:image/x-emf;base64...)

1. 请求查询交易记录流程

流程说明：

* 1. APP\_SERVER发送查询交易请求到ITP平台。
  2. ITP 接收到请求后。

如验证请求参数（基础参数、合作伙伴信息及签名）验证失败，则返回retCode：8001；

如验证用户是未注册用户，则返回retCode:8004；

如验证用户状态为解约审核中，则返回retCode:8008；

如验证用户卡号是否与请求的卡号参数不一致，则返回retCode:8006；

查询用户交易记录，处理完成后返回retCode:0000；

业务处理过程中出现异常时，则返回retCode:9999。

* 1. ITP平台将交易记录返回给APP\_SERVER。

1. 请求解约

请求解约流程详见图6。

![](data:image/x-emf;base64...)

1. 请求解约流程

流程说明：

* 1. 乘客或APP\_SERVER 发送解约申请到ITP平台。
  2. ITP接收到解约请求后。

如验证请求参数（基础参数、合作伙伴信息及签名）验证失败，则返回retCode：8001；

如验证用户是未注册用户，则返回retCode:8004；

如验证用户状态为解约审核中，则返回retCode:8008；

如验证用户卡号是否与请求的卡号参数不一致，则返回retCode:8006；

更新用户状态标识为解约状态，处理完成后返回retCode:0000；

业务处理过程中出现异常时，则返回retCode:9999。

* 1. ITP平台返回请求解约结果。
  2. ITP等待平台扣费周期结束后向支付通道发起解约请求。
  3. 支付通道将解约结果返回给ITP平台。
  4. ITP将解约结果推送给APP\_SERVER。

1. 行业数据通知

行业数据通知流程详见图7。

![](data:image/x-emf;base64...)

1. 行业数据通知流程

流程说明：

* 1. APP\_SERVER发送补进/出站请求到ITP平台。
  2. ITP平台接收到请求后。

如验证请求参数（基础参数、合作伙伴信息及签名）验证失败，则返回retCode：8001；

如验证用户是未注册用户，则返回retCode:8004；

如验证用户状态为解约审核中，则返回retCode:8008；

如验证用户卡号是否与请求的卡号参数不一致，则返回retCode:8006；

BOM更新后的票卡无法自助更新，则返回retCode:8300；

票卡状态正常，可正常进出站，则返回retCode:8301；

补出站成功，则返回retCode:8302；

补进站成功，则返回retCode:8303；

一天内自助补站次数达到上限，则返回retCode:8304；

补进/出站业务正常处理完成后返回retCode:0000；

业务处理过程中出现异常时，则返回retCode:9999。

* 1. ITP业务处理过程中，如票卡状态变更需要把变更的行业数据推送给 APP\_SERVER。
  2. APP\_SERVER接收到票卡状态变更后推送行业数据给地铁APP客户端。

1. 解约结果通知

解约结果通知流程详见图8。

![](data:image/x-emf;base64...)

1. 解约结果通知流程

流程说明：

* 1. APP\_SERVER发送请求解约申请到ITP平台。
  2. ITP接收到解约请求后。

如验证请求参数（基础参数、合作伙伴信息及签名）验证失败，则返回retCode：8001；

如验证用户是未注册用户，则返回retCode:8004；

如验证用户状态为解约审核中，则返回retCode:8008；

如验证用户卡号是否与请求的卡号参数不一致，则返回retCode:8006；

标记用户为待解约审核中状态，处理完成后返回retCode:0000；

业务处理过程中出现异常时，则返回retCode:9999。

* 1. ITP返回请求结果给APP\_SERVER（解约结果等待审批结束）。
  2. ITP在单边扣费周期结束后，将解约请求发送给支付通道。
  3. 支付通道把解约结果返回给ITP平台。
  4. ITP把解约结果推送给APP\_SERVER。

业务规则：

乘客在付费区，不可以发起解约。

1. 黑名单结果通知

黑名单结果通知流程详见图9。

![](data:image/x-emf;base64...)

1. 黑名单结果通知流程

流程说明：

* 1. ITP平台定期把黑名单列表推送给APP\_SERVER服务器端。

1. AGM CA公钥变更通知

AGM CA公钥变更通知流程详见图10。

![](data:image/x-emf;base64...)

1. AGM CA公钥变更通知流程

流程说明：

* 1. ITP平台变更AGM CA公钥后通知APP\_SERVER ,APP\_SERVER收到通知后，通知客户端同步用户密钥。

1. 设置默认支付通道

设置默认支付通道流程详见图11。

![](data:image/x-emf;base64...)

1. 设置默认支付通道流程

流程说明：

* 1. 用户在已经签约的支付通道列选择要设置的默认的支付通道。
  2. 地铁APP请求设置默认支付通道接口，ITP设置用户默认支付通道。
  3. 设置默认通道成功后，客户端发起请求行业数据接口。

异常流程:

* 1. 接口传送的逻辑卡号不存在，接口返回没有账号卡片数据。
  2. 接口传送要设置的支付通道未签约，接口返回无效的签约数据。
  3. 接口传送要设置的支付通道解约中，接口返回解约审核中。
  4. 后付费HCE业务

1. 请求开户HCE后付费

请求开户HCE后付费流程详见图12。

![](data:image/x-emf;base64...)

1. 请求开户HCE后付费流程

流程说明：

* 1. 安卓手机检查是否支持NFC功能，如果支持引导用户打开NFC功能。
  2. 用户同意开通HCE后付费业务。
  3. APP客户端向APP\_SERVER请求默认支付通道信息。APP服务器返回用户默认支付通道信息。
  4. APP客户端向APP\_SERVER发起开户HCE后付费信息。APP\_SERVER向ITP平台发起开卡请求。
  5. ITP平台收到开户请求，检查支付通道信息，完成开户逻辑，返回给APP\_SERVER开户结果，APP\_SERVER收到开户结果，保存用户开通业务逻辑。返回给APP客户开户结果。
  6. APP客户端收到开户成功结果，发起请求密钥同步和请求行业数据接口。收到服务器返回的成功应答后。显示开户成功信息。

异常流程:

* 1. 如果安卓手机不支持NFC功能，APP可以不显示开通HCE业务入口后有入口提示手机不支持NFC。
  2. 如果APP\_SERVER返回无支付通道信息，客户端发起认证及签约流程。
  3. 如果开户返回超时，APP\_SERVER可以重试开户逻辑，如果已经开户，返回已经开户，如果未开户，完成开户。

1. 请求同步密钥

请求同步密钥流程详见图13。

![](data:image/x-emf;base64...)

1. 请求同步密钥流程

流程说明：

* 1. 客户端检查HCE密钥是否存在客户端本地，如果不在APP客户端向APP发起请求同步密钥接口。如果APP\_SERVER本地也没有密钥，APP\_SERVER向ITP平台发起密钥同步请求。
  2. ITP收到密钥同步请求检查本地数据库是否保存HCE密钥，如果有直接用APP\_SERVER和ITP约定的KEK转加密给APP\_SERVER。[密钥类型对称密钥]。

异常流程：

* 1. 如果ITP平台无缓存的HCE密钥向ACC发起请求HCE消费密钥请求，ACC返回密钥，ITP平台保存密钥值。

1. 请求行业数据

请求行业数据流程详见图14。

![](data:image/x-emf;base64...)

1. 请求行业数据流程

流程说明:

* 1. 用户打开HCE服务，检查HCE后付费是否已经默认选中，如果没有选中HCE票，引导用户选择HCE票。
  2. 客户端发起信用能力咨询接口。服务器返回信用能力结果。
  3. 客户端查找本地缓存的HCE数据。
  4. 显示HCE票卡信息。

异常流程:

* 1. 超时1秒没返回，忽略查询信用接口，直接往下走逻辑。
  2. 客户端本地没有HCE缓存，APP客户端向APP服务器发起请求行业数据同步HCE数据。
  3. 互联网售票业务

1. APP购票

APP购票流程详见图15。

![](data:image/x-emf;base64...)

1. APP购票流程

处理流程：

* 1. 地铁APP获取线路编码。地铁APP请求APP\_SERVER获取线路代码，APP\_SERVER请求ITP，ITP存缓存中获取线路信息，并将线路信息返回。
  2. 地铁APP获取站点编码。地铁APP请求APP\_SERVER获取站点代码，APP\_SERVER请求ITP，ITP存缓存中获取站点信息，并将站点信息返回。
  3. 地铁APP获取最大购票数。地铁APP请求APP\_SERVER获取最大购票数，APP\_SERVER请求ITP，ITP存缓存中获取最大购票数，并将最大购票数返回。
  4. 地铁APP计算票价。地铁APP请求APP\_SERVER计算票价，APP\_SERVER请求ITP，ITP根据起止站点计算票价，并将票价返回。
  5. 选择支付通道。

1. 支付

支付流程详见图16。

![](data:image/x-emf;base64...)

1. 支付流程

处理流程：

* 1. 请求支付。地铁APP向APP\_SERVER请求支付，APP\_SERVER请求ITP，ITP校验请求参数合法性，若参数不合法则返回错误码8001。如果参数合法创建订单，根据支付通道编码payChannelCode，创建商户订单号，请求对应的支付通道预下单，将预下单返回的支付信息签名，将签名后的订单信息返回。
  2. 支付订单。地铁APP验签，请求支付通道支付订单。
  3. 支付结果通知。ITP收到支付结果通知，判断支付结果，如果支付成功将订单状态更新为已支付，记录支付时间，支付金额等。

1. 支付结果通知

付结果通知流程详见图17。

![](data:image/x-emf;base64...)

1. 付结果通知流程

流程说明：

* 1. ITP收到支付通道的支付结果通知后，判断支付结果，如果支付成功将订单状态更新为已支付，记录支付时间，支付金额等。
  2. ITP将支付结果通知APP\_SERVER，APP\_SERVER更新对应的订单状态，支付时间支付金额等。
  3. 若订单在地铁APP已经支付成功后，但订单状态还是已下单状态时，地铁APP可主动发起订单状态查询，APP\_SERVER将请求ITP查询订单状态，如果ITP的订单状态是已下单状态，则请求对应的支付通道查询订单状态，根据返回结果将支付状态返回给APP\_SERVER。如果ITP中订单状态为已支付，建直接给APP\_SERVER返回订单支付成功。
  4. 当ITP收到TVM出票结果的通知后，ITP将出票结果通知推送到APP\_SERVER。

1. 请求退款

请求退款流程详见图18。

![](data:image/x-emf;base64...)

1. 请求退款流程

流程说明：

* 1. 地铁APP请求退款时，APP\_SERVER将退款请求发送到ITP，ITP收到请求后判断用户的user\_id是否与订单信息中的user\_id是否一致，如果不一致则返回8001，如果一致则判断订单状态是否为已支付状态，如果不是则返回8305，如果订单状态为已支付，则请求支付通道请求退款。
  2. ITP收到支付通道退款结果通知后，更新订单状态为已退款，记录退款时间。并将退款结果通知给APP\_SERVER。
  3. APP\_SERVER收到退款结果通知后，将订单状态更新为已退款，记录退款时间，退款金额。
  4. 若当日的单程票在当日未使用，则在次日凌晨发起批量退款，若退款成功，ITP将退款结果通知给APP\_SERVER。

1. 激活取票订单

激活取票订单流程详见图19。

![](data:image/x-emf;base64...)

1. 激活取票订单流程

流程说明：

* 1. 取票订单激活。用户在地铁APP上查询待取票的订单列表，选择需要取票的订单，打开扫一扫，对TVM上的取票码扫码，点击激活。
  2. APP\_SERVER将订单激活请求发送到ITP。
  3. ITP收到激活订单的请求后先判断用户所扫的设备是否属于订单中起点站的设备,如果不是则返回错误码8306， 判断用户的user\_id是否与订单信息中的user\_id是否一致，如果不一致则返回8001，判断订单状态是否为已支付如果订单状态未支付则返回错误码8305。如果验证通过后将激活的订单号与设备编码和设备校验码绑定保存在数据库中。
  4. TVM查询取票订单。ITP校验请求对象中设备编号是否合法，如果不合法返回错误码2001（非法设备）。如果设备合法根据请求的设备编号及校验码查询已激活的订单，如果有已激活的订单，则返回订单信息。如果没有，则返回错误码2003。
  5. 设备判断返回码。如果没有该设备对应的已激活的订单将继续向ITP发起取票订单查询，超过一定时间TVM放弃查询。如果有已激活的订单则开始出票。
  6. TVM出票成功。ITP校验请求对象中设备编号是否合法，如果不合法返回错误码2001（非法设备）。将订单状态更新为已出票，记录出票时间，出票张数。向APP\_SERVER推送出票结果。
  7. TVM出票故障。记录故障信息，及出票张数，出票时间，并计算未出票的金额向支付通道发起退款，根据支付通道返回的退款结果更新订单状态，如果退款成功将订单状态更新为已退款。向APP\_SERVER推送退款结果。
  8. TVM生成本地交易记录。

1. 数据接口
   1. IF8A-01 请求开户
2. 接口地址

http//:[ip]:[port]/[project]/ ci/app/requestApplication

1. 接口说明

APP请求ITP平台进行用户实名开户预付费二维码接口

1. 请求参数

请求开户请求参数详见表5。

1. 请求开户请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| thirdUserId | String | 第三方用户id |
| thirdPayId | String | 支付账户id（签约回调）默认空 |
| channel | String | 支付通道编码 默认空 |
| reqContractNo | String | 第三方签约流水号（请求签约流水）默认空 |
| cardType | String | 卡类型编码 |
| msisdn | String | 手机号 |
| userName | String | 姓名 青岛APP可以默认空 |
| userId | String | 用户身份证号青岛  APP可以默认空 |
| extend1 | String | 扩展字段 |
| extend2 | String | 扩展字段 |
| ticketCard | String | NFC开卡使用的字段，01是非钱包NFC卡，02是钱包NFC卡 非必填 |
| companionFlag | String | 同行票是Y，第三方是C，目前两种标识对应的卡类型都是0441 非必填 |
| ticketLimit | String | 车票限制 1需要同一所属方限制开卡数量为1；2可开多张，每次请求都给一张新卡 |
| cardIssueCode | String | 票卡所属方，不固定，目前有地铁APP、支付宝出行、海上巴士、其他互通APP等，不需要校验是否存在 |

1. 应答参数

请求开户应答参数详见表6。

1. 请求开户应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |
| cardId | String | 地铁会员卡号 |
| cardType | String | 卡类型编码 |
| signType | string | 00：不签名  01：sha1withrsa  02：MD5 |
| sign | string | 私钥签名sha1withrsa |

* 1. IF8A-02 请求同步密钥

1. 接口地址

http//:[ip]:[port]/[project]/ ci/app/requestKeyList

1. 接口说明

APP请求ITP获取用户密钥

1. 请求参数

请求同步密钥请求参数详见表7。

1. 请求同步密钥请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| thirdUserId | string | 第三方用户id |
| cardId | String | 地铁会员卡号 |
| cardType | String | 2.8 卡类型编码 |

1. 应答参数

请求同步密钥应答参数详见表8。

1. 请求同步密钥应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |
| keyList | json数字 | 密钥列表 |
| signType | string | 00：MD5  01：sha1withrsa |
| sign | string | 私钥签名sha1withrsa |

单条密钥

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| keyId | String | 密钥编码  00：工作密钥  01：用户非对称密钥对 |
| keyType | string | 密钥类型  0：对称密钥  1：非对称密钥 |
| keyUserId | string | 非对称密钥运算的用户标识 |
| keyPrivate | string | 非对称用户私钥 |
| keyPublic | string | 非对称用户公钥X |
| keyPublicEffectiveDate | string | 公钥有效期默认7天 |
| signData | string | 非对称用户公钥签名数据 |
| caIdx | string | 非对称公钥验签密钥索引 |
| keyWrapValue | string | 对称密钥值 |
| keyEffectiveDate | string | 对称密钥有效期 |
| kvc | string | 对称密钥KVC |
| reserve | string | 预留字段 |

* 1. IF8A-03 请求行业数据

1. 接口地址

http//:[ip]:[port]/[project]/ci/app/requestIndustryData

1. 接口说明

APP请求ITP获取行业数据

1. 请求参数

请求行业数据请求参数详见表9。

1. 请求行业数据请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| thirdUserId | string | 第三方用户id |
| cardId | String | 地铁会员卡号 |
| cardType | String | 2.8 卡类型编码 |

1. 应答参数

请求行业数据应答参数详见表10。

1. 请求行业数据应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |
| cardData | string | 行业数据 编码格式Hexstring |
| signType | string | 00：不签名  01：sha1withrsa  02：MD5 |
| sign | string | 私钥签名sha1withrsa |

* 1. IF8A-04 请求用户自助补站

1. 接口地址

http//:[ip]:[port]/[project]/ci/app/requestExcessFare

1. 接口说明

APP请求ITP进行自助补票

1. 请求参数

请求用户自助补站请求参数详见表11。

1. 请求用户自助补站请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| thirdUserId | string | 第三方用户id |
| cardId | String | 地铁会员卡号 |
| cardType | String | 2.8 卡类型编码 |
| upgradeAreaType | String | 01：补进站  02：补出站 |
| upgradeStationCode | String | 补进/出站编码 |
| upgradeReason | String | 更新原因预留 |
| upgradeDateTime | String | 更新操作时间YYYYMMDD24mmss |

1. 应答参数

请求用户自助补站应答参数详见表12。

1. 请求用户自助补站应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

* 1. IF8A-05 请求查询交易记录

1. 接口地址

http//:[ip]:[port]/[project]/ci/app/requestTransList

1. 接口说明

APP请求ITP获取用户交易记录

1. 请求参数

请求查询交易记录请求参数详见表13。

1. 请求查询交易记录请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| thirdUserId | string | 第三方用户id |
| cardId | String | 地铁会员卡号 支持多卡，逗号隔开 |
| cardType | String | 卡类型编码 |
| pageNumber | Int | 当前页 |
| pageSize | Int | 一页记录条数 |
| totalPage | Int | 总页数 |
| startDate | String | 开始日期 非必填 yyyy-MM-dd |
| endDate | String | 结束日期 非必填 yyyy-MM-dd |
| debitRequestResult | String | 扣款结果 空查全部，0查成功，1查失败 |
| ticketCode | String | 日票票号 |

1. 应答参数

请求查询交易记录应答参数详见表14。

1. 请求查询交易记录应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |
| pageNumber | string | 当前页 |
| pageSize | string | 一页记录条数 |
| totalPage | string | 总页数 |
| ticketTransRecord | json数组 |  |
| signType | string | 00：不签名  01：sha1withrsa  02：MD5 |
| sign | string | 私钥签名sha1withrsa |

单条

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| entryStationName | String | 进站名称 |
| entryDate | String | 进站时间YYYYMMDDHH24mmss |
| exitStationName | String | 出站名称 |
| exitDate | String | 出站时间YYYYMMDDHH24mmss |
| payAmount | String | 支付金额（单位分） |
| orderExpType | String | 订单异常类型  0 正常  1 单边账(入站)  2 单边账(出站)  3 单边入站(人工处理单)  4 单边出站(人工处理单)  5 双段计费正常订单\_行程超时 |
| tradeOrderNo | String | 商户订单号 |
| payTradeOrderNo | String | 支付交易订单号 |
| payOrderNoDate | String | 扣款时间 |
| payChannelCode | String | 2.7 支付通道编码 |
| debitRequestResult | String | 扣款结果 |
| discountFee | Int | 优惠金额（分） |
| discountInfo |  | 优惠详情（json数组） |
| companionFlag | String | 同行票标识 Y/N |
| cardNum | String | 卡号 |
| ticketCode | String | 日票的票号 |
| countingTimes | Int | 计次票扣减次数 |
| countingFlag | String | 是否是计次票 Y/N |
| offlineFlag | String | 离线码标识 |
| attributableParty | String | 订单应收商户 |
| receivingParty | String | 订单实收商户 |

* 1. IF8A-06 请求解约

1. 接口地址

http//:[ip]:[port]/[project]/ci/app/requestTermination

1. 接口说明

APP请求ITP平台进行解约

1. 请求参数

请求解约请求参数详见表15。

1. 请求解约请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| thirdUserId | string | 第三方用户id |
| cardId | String | 地铁会员卡号 |
| cardType | String | 卡类型编码 |
| requestSignSeq | string | 签约请求流水号 |

1. 应答参数

请求解约应答参数详见表16。

1. 请求解约应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

* 1. IF8A-07 获取线路代码

1. 接口地址

http//:[ip]:[port]/[project]/ci/app/requestLineCodeList

1. 接口说明

APP请求ITP获取线路代码信息列表

1. 请求参数

获取线路代码请求参数详见表17。

1. 获取线路代码请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
|  |  |  |

1. 应答参数

获取线路代码应答参数详见表18。

1. 获取线路代码应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |
| lineCodeRecord | Json数组 | 线路列表 |

单条

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| lineCode | string | 线路代码 |
| lineNameZH | string | 线路中文名称 |
| lineNameEN | string | 线路英文名称 |
| orderIndex | string | 排序字段 |

* 1. IF8A-08 获取车站代码

1. 接口地址

http//:[ip]:[port]/[project]/ci/app/requestStationCodeList

1. 接口说明

APP请求ITP平台获取站点代码信息列表

1. 请求参数

获取车站代码请求参数详见表19。

1. 获取车站代码请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| lineCode | string | 线路代码 |

1. 应答参数

获取车站代码应答参数详见表20。

1. 获取车站代码应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |
| stationCodeRecord | Json数组 |  |

单条

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| lineCode | string | 线路代码 |
| stationCode | string | 站点代码 |
| stationNameZH | string | 站点名称(中文) |
| stationNameEN | string | 站点名称(英文) |
| transferYn | string | 是否换乘(Y/N) |

* 1. IF8A-09 获取购买最多张数

1. 接口地址

http//:[ip]:[port]/[project]/ci/app/requestBuySinlgeTicketMaxNum

1. 接口说明

APP请求ITP平台获取单次可购买最大车票张数

1. 请求参数

获取购买最多张数请求参数详见表21。

1. 获取购买最多张数请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
|  |  |  |

1. 应答参数

获取购买最多张数应答参数详见表22。

1. 获取购买最多张数应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |
| buySinlgeTicketMaxNum | string | 单次可购买最大车票张数 |

* 1. IF8A-10 计算票价

1. 接口地址

http//:[ip]:[port]/[project]/ci/app/requestTicketPriceByStation

1. 接口说明

APP请求ITP平台获取行程票价

1. 请求参数

计算票价请求参数详见表23。

1. 计算票价请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| entryStationCode | string | 起点站点代码 |
| exitStationCode | string | 终点站点代码 |

1. 应答参数

计算票价应答参数详见表24。

1. 计算票价应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |
| ticketPrice | string | 票价单位分 |

* 1. IF8A-11 请求支付

1. 接口地址

http//:[ip]:[port]/[project]/ci/app/requestPaymentInfo

1. 接口说明

请求支付信息

1. 请求参数

请求支付请求参数详见表25。

1. 请求支付请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| orderNo | string | 订单号 |
| payChannelCode | string | 支付通道编码定义 |
| channelType | String | 下单渠道 1地铁APP，tradeType03 2青E办（已经不用了）tradeType06 |

1. 应答参数

请求支付应答参数详见表26。

1. 请求支付应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |
| payChannelCode | string | 2.7支付通道编码定义 |
| paymentInfo | string | 支付信息，根据payChannelCode返回内容 |
| signType | string | 00：不签名  01：sha1withrsa  02：MD5 |
| sign | string | 私钥签名sha1withrsa |

报文示例：

10001 支付宝SDK

app\_id=2015052600090779&biz\_content=%7B%22timeout\_express%22%3A%2230m%22%2C%22seller\_id%22%3A%22%22%2C%22product\_code%22%3A%22QUICK\_MSECURITY\_PAY%22%2C%22total\_amount%22%3A%220.02%22%2C%22subject%22%3A%221%22%2C%22body%22%3A%22%E6%88%91%E6%98%AF%E6%B5%8B%E8%AF%95%E6%95%B0%E6%8D%AE%22%2C%22out\_trade\_no%22%3A%22314VYGIAGG7ZOYY%22%7D&charset=utf-8&method=alipay.trade.app.pay&sign\_type=RSA2&timestamp=2016-08-15%2012%3A12%3A15&version=1.0&sign=MsbylYkCzlfYLy9PeRwUUIg9nZPeN9SfXPNavUCroGKR5Kqvx0nEnd3eRmKxJuthNUx4ERCXe552EV9PfwexqW%2B1wbKOdYtDIb4%2B7PL3Pc94RZL0zKaWcaY3tSL89%2FuAVUsQuFqEJdhIukuKygrXucvejOUgTCfoUdwTi7z%2BZzQ%3D

10002 微信支付SDK

{

"app\_Id" ： "wx2421b1c4370ec43b", //应用ID

"partnerid " ： "1900000109", //商户号

"partnerid " ： " WX1217752501201407033233368018", //预支付交易会话ID

"package" ： " Sign=WXPay ", //订单详情扩展字符串

"nonce\_str" ： "e61463f8efa94090b1f366cccfbbb444", //随机串

"time\_stamp"：" 1395712654", //时间戳，自1970 年以来的秒数

"sign" ： "70EA570631E4BB79628FBCA90534C63FF7FADD89" //微信签名

signValue : 1SMDLFK34509LKMLKAJSDL1U349?ASDOIU4CT9

}

10003 联支付SDK

{

tn=442715340596456143810

}

* 1. IF8A-12 请求退款

1. 接口地址

http//:[ip]:[port]/[project]/ci/app/requestRefundTicket

1. 接口说明

请求退款,同步返回退款结果

1. 请求参数

请求退款请求参数详见表27。

1. 请求退款请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| userId | string | 用户编码 |
| orderNo | string | 订单号 |
| refundReason | String | 退款原因 |

1. 应答参数

请求退款应答参数详见表28。

1. 请求退款应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |
| orderNo | string | 订单号 |
| refundType | string | 00: 用户提交退款  01：超时未使用自动退款 |
| refundResult | string | SUCCESS/FAIL/PROCESSING  SUCCESS—退款成功  FAIL—退款失败  PROCESSING-退款进行中 |
| refundResultDesc | string | 退款描述 |
| refundDate | string | 退款日期 |
| refundAmount | String | 退款金额 单位分 |

* 1. IF8A-13 退款结果查询

1. 接口地址

http//:[ip]:[port]/[project]/ci/app/requestRefundTicketResult

1. 接口说明

请求退款，异步返回退款结果

1. 请求参数

退款结果查询请求参数详见表29。

1. 退款结果查询请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| userId | string | 用户编码 |
| orderNo | string | 订单号 |

1. 应答参数

退款结果查询应答参数详见表30。

1. 退款结果查询应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |
| orderNo | string | 订单号 |
| refundType | string | 00: 用户提交退款  01：超时未使用自动退款 |
| refundResult | string | SUCCESS/FAIL/PROCESSING  SUCCESS—退款成功  FAIL—退款失败  PROCESSING-退款进行中 |
| refundResultDesc | string | 退款描述 |
| refundDate | string | 退款日期 |
| refundAmount | String | 退款金额 单位分 |

* 1. IF8A-14 获取激活取票订单

1. 接口地址

http//:[ip]:[port]/[project]/ci/app/requestPreActiveOrderList

1. 接口说明

获取我的订单列表,用于激活取票订单,预留接口

1. 请求参数

获取激活取票订单请求参数详见表31。

1. 获取激活取票订单请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| userId | String | 订单用户编码 |
| appType | String | 01:青岛地铁  其他预留 |

1. 应答参数

获取激活取票订单应答参数详见表32。

1. 获取激活取票订单应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |
| orderList | json数组 | 用户 |

单条

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| orderNo | String | 订单号 |
| entryStationCode | string | 起点站点代码 |
| exitStationCode | string | 终点站点代码 |
| ticketPrice | string | 票价单位分 |
| singelTicketNum | string | 购买数量 |
| singleTicketType | string | 0:有起点站和终点站 |
| orderDate | String | 下单时间 |
| payDate | String | 支付时间 |

* 1. IF8A-15 激活取票订单

1. 接口地址

http//:[ip]:[port]/[project]/ci/tvm/requestActiveTicket

1. 接口说明

激活取票订单

1. 请求参数

激活取票订单请求参数详见表33。

1. 激活取票订单请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| orderNo | String | 订单号 |
| deviceId | string | 取票设备编码 |
| qrcodeGenDate | string | 设备取票二维码生成时间 |
| randomFact | string | 设备随机因子 |

1. 应答参数

激活取票订单应答参数详见表34。

1. 激活取票订单应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

* 1. IF8A-16 请求签约请求信息

1. 接口地址

http//:[ip]:[port]/[project]/ci/tvm/requestSignInfo

1. 接口说明

请求签约调用SDK所需的签名信息

1. 请求参数

请求签约请求信息请求参数详见表35。

1. 请求签约请求信息请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| thirdUserId | String | 第三方用户编码 |
| displayAccount | string | 签约显示账号（用于只发布微信签约界面，可以用户昵称，手机号，用户姓名） |
| payChannelCode | string | 2.7支付通道编码定义 |
| requestSignSeq | string | 签约请求流水号 |
| returnUrl | String | 支付插件回调签约地址 |
| authCode | String | 授权码 |

1. 应答参数

请求签约请求信息应答参数详见表36。

1. 请求签约请求信息应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |
| requestStartSdkInfo | string | 调用SDK所需的请求参数 |

支付宝

alipays://platformapi/startapp?appId=60000157&appClearTop=false&startMultApp=YES&sign\_params=app\_id%3D2015101000413186……

微信

https://api.mch.weixin.qq.com/papay/entrustweb?appid=wx426a3015555a46be&contract\_code=122&contract\_display\_account=name1……

* 1. IF8A-17 获取线路站点代码版本

1. 接口地址

http//:[ip]:[port]/[project]/ci/app/requestLineStationCodeVersion

1. 接口说明

获取当前生效的车站代码线路代码版本号

1. 请求参数

获取线路站点代码版本请求参数详见表37。

1. 获取线路站点代码版本请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
|  |  |  |

1. 应答参数

获取线路站点代码版本应答参数详见表38。

1. 获取线路站点代码版本应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |
| lineCodeIdx | string | 线路版本号 |
| stationCodeIdx | string | 站点版本号 |
| changeDate | string | 更新日期 |

* 1. IF8A-18 支付结果查询

1. 接口地址

http//:[ip]:[port]/[project]/ci/app/requestPayResult

1. 接口说明

客户端收到支付插件支付成功通知，没收到服务器异步支付结果通知，客户端服务器主动向ITP平台发起支付结果查询，如果ITP平台收到支付结果直接返回支付结果；如果ITP平台也没有收到支付结果回调，ITP平台主动向第三方支付通知发起支付结果查询；返回给客户端服务器。

1. 请求参数

支付结果查询请求参数详见表39。

1. 支付结果查询请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| userId | string | 用户编码 |
| orderNo | string | 订单号 |

1. 应答参数

支付结果查询应答参数详见表40。

1. 支付结果查询应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |
| tradeNo | string | 支付交易流水号 |
| payResult | string | SUCCESS/FAIL |
| payAmount | string | 支付金额 |
| payDate | string | 支付时间 |

* 1. IF8A-19 BLE通知闸机检票通知

1. 接口地址

http//:[ip]:[port]/[project]/ci/app/notiAgmVerifyResult

1. 接口说明

闸机验证二维码合法，蓝牙会写到APP，APP通知ITP验签结果。

1. 请求参数

BLE通知闸机检票通知请求参数详见表41。

1. BLE通知闸机检票通知请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| itpUserId | String | ITP用户编码 二维码中数据 |
| trxType | String | 交易类型  01进站 蓝牙半字节状态 1  02出站 蓝牙半字节状态 2  03超时出站蓝牙半字节状态 9 |
| issueChannelCode | String | 01：青岛地铁 二维码中数据 |
| signChannelCode | String | 签约通道编码 二维码中数据  01: 微信签约代扣  02: 支付宝签约代扣 |
| cardId | String | 逻辑卡号 二维码中数据 |
| cardType | String | 2.8 卡类型编码 |
| handleDeviceCode | String | 交易闸机编码 |
| handleDateTime | String | 闸机交易时间YYYYMMDDHH24MISS |
| handleStationCode | String | 闸机交易车站代码 |
| trxAmount | String | 实际交易金额（单位分） |
| overtimeAmount | String | 超时金额（单位分） |
| lastTicketStatus | String | 二维码中数据  02: 结束行程  03：SJT发售（init 2）  04：已进站（entry）  05：已出站（exit）  06：超时出站  08：20分钟内免费更新（BOM/pca非付费区）  09：20分钟内付费更新（BOM/pca非付费区）  10：入站码更新（BOM/pca付费区）  80: 用户自助补出站  81：用户自助补进站 |
| handleResultCode | String | 闸机处理结果 |
| lastHandleStationCode | String | 二维码中数据  上次交易车站代码 |
| lastHandleDateTime | String | 二维码中数据  上次交易时间YYYYMMDDHH24MISS |
| tikcetTransSeq | String | 二维码中的数据  交易计数器 |
| reserve1 | String | 预留字段1 |
| reserve2 | String | 预留字段2 |

1. 应答参数

BLE通知闸机检票通知应答参数详见表42。

1. BLE通知闸机检票通知应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

* 1. IF8A-20 请求下单

1. 接口地址

http//:[ip]:[port]/[project]/ci/app/requestOrder

1. 接口说明

请求下单

1. 请求参数

请求下单请求参数详见表43。

1. 请求下单请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| userId | string | 用户编码 |
| entryStationCode | string | 起点站点代码 |
| exitStationCode | string | 终点站点代码 |
| ticketPrice | string | 票价单位分 |
| singelTicketNum | string | 购买数量 |
| singleTicketType | string | 0:有起点站和终点站 |
| channelCode | String | 下单渠道 非必填 |

1. 应答参数

请求下单应答参数详见表44。

1. 请求下单应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |
| orderNo | string | 订单号 |

* 1. IF8A-21 信用能力咨询

1. 接口地址

http//:[ip]:[port]/[project]/ci/app/requestContractAdvisory

1. 接口说明

信用能力咨询

1. 请求参数

信用能力咨询请求参数详见表45。

1. 信用能力咨询请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| thirdUserId | string | 第三方用户编码 |
| requestSignSeq | string | 签约请求流水号 |
| paymentVendor | string | 支付类型  03 支付宝  04 微信支付  06 建行龙支付  07 银联云闪付  0B 钱包支付 |

1. 应答参数

信用能力咨询应答参数详见表46。

1. 信用能力咨询应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

* 1. IF8A-22 签约结果咨询

1. 接口地址

http//:[ip]:[port]/[project]/ci/app/requestContractResult

1. 接口说明

签约结果查询

1. 请求参数

签约结果咨询请求参数详见表47。

1. 签约结果咨询请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| thirdUserId | string | 第三方用户编码 |
| requestSignSeq | string | 签约请求流水号 |
| paymentVendor | string | 支付类型  03 支付宝  04 微信支付  06 建行龙支付  07 银联云闪付  0B 钱包 |

1. 应答参数

签约结果咨询应答参数详见表48。

1. 签约结果咨询应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |
| status | string | 签约状态  未签约NOT\_SIGNED,  已签约SIGNED  解除签约UNSIGNED |
| payAccountId | string | 支付用户编码 |
| payAgreementNo | string | 支付通道签约流水号 |

* 1. IF8A-23 请求添加支付通道

1. 接口地址

http//:[ip]:[port]/[project]/ ci/app/requestAddPayChannel

1. 接口说明

请求新增支付通道

1. 请求参数

请求添加支付通道请求参数详见表49。

1. 请求添加支付通道请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| thirdUserId | String | 第三方用户id |
| cardId | String | 地铁会员卡号 |
| cardType | String | 2.8 卡类型编码 |
| channel | String | 2.7 支付通道编码 |
| thirdPayId | String | 支付账户id（签约回调） |
| reqContractNo | String | 第三方签约流水号（请求签约流水） |

1. 应答参数

请求添加支付通道应答参数详见表50。

1. 请求添加支付通道应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

* 1. IF8A-24 请求设置默认支付通道

1. 接口地址

http//:[ip]:[port]/[project]/ ci/app/requestSetDefaultPayChannel

1. 接口说明

修改默认支付通道

1. 请求参数

请求设置默认支付通道请求参数详见表51。

1. 请求设置默认支付通道请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| thirdUserId | String | 第三方用户id |
| cardId | String | 地铁会员卡号 |
| cardType | String | 2.8 卡类型编码 |
| channel | String | 2.7 支付通道编码 |

1. 应答参数

请求设置默认支付通道应答参数详见表52。

1. 请求设置默认支付通道应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

* 1. IF8A-25 请求实名（不需要了）

1. 接口地址

http//:[ip]:[port]/[project]/ ci/app/requestRealNameVerify

1. 接口说明

请求实名认证

1. 请求参数

请求实名请求参数详见表53。

1. 请求实名请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| thirdUserId | String | 第三方用户编码 |
| userName | string | 用户名  paymentVendor=05必填 |
| certNo | string | 身份证号  paymentVendor=05必填 |
| mobile | string | 银行卡绑定的手机号  paymentVendor=05必填 |
| bankCardNo | string | 银行卡号码  paymentVendor=05必填 |
| authToken | string | 微信授权code  paymentVendor=04必填 |
| paymentVendor | string | 支付类型  04 微信  05 银联 |

1. 应答参数

请求实名应答参数详见表54。

1. 请求实名应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

* 1. IF8B-01 行业数据推送

1. 接口地址

http//:[ip]:[port]/[project]/ticket/receiveCardDataFromItp

1. 接口说明

推送行业数据

1. 请求参数

行业数据推送请求参数详见表55。

1. 行业数据推送请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| thirdUserId | string | 第三方用户id |
| cardId | String | 地铁会员卡号 |
| cardType | String | 卡类型编码 |
| cardData | String | 卡数据HexString |
| companionFlag | String | 同行票标识 |
| entryDeviceCode | String | 进站设备码 |
| exitDeviceCode | String | 出站设备码 |

1. 应答参数

行业数据推送应答参数详见表56。

1. 行业数据推送应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

* 1. IF8B-02 解约结果通知

1. 接口地址

http//:[ip]:[port]/[project]/ticket/receiveTerminationResultFromItp

1. 接口说明

解约结果通知

1. 请求参数

解约结果通知请求参数详见表57。

1. 解约结果通知请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| thirdUserId | string | 第三方用户id |
| cardId | String | 地铁会员卡号 |
| cardType | String | 2.8 卡类型编码 |
| requestSignSeq | String | 签约流水号 |
| terminationResult | String | 解约结果  SUCCESS成功  FAIL失败 |
| terminationResultMsg | String | 结果描述 |
| terminationTime | string | 解约时间, 格式如：  YYYYMMDDHH24mmss |

1. 应答参数

解约结果通知应答参数详见表58。

1. 解约结果通知应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

* 1. IF8B-03 黑名单结果通知

1. 接口地址

http//:[ip]:[port]/[project]/app/receiveBlackListFromItp

1. 接口说明

黑名单结果通知

1. 请求参数

黑名单结果通知请求参数详见表59。

1. 黑名单结果通知请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| blackList | Json数组 | 黑名单列表, 最多25笔 |

单条记录

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| thirdUserId | string | 第三方用户id |
| cardId | String | 地铁会员卡号 |
| cardType | String | 2.8 卡类型编码 |
| blackListType | String | 解约结果  1：加入黑名单  2：移除黑名单 |
| optionDate | String | 操作时间时间, 格式如：  YYYYMMDDHH24mmss |
| expireTime | String | 黑名单有效期, 格式如：  YYYYMMDDHH24mmss。  当解约结果为1时有效 |

1. 应答参数

黑名单结果通知应答参数详见表60。

1. 黑名单结果通知应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

* 1. IF8B-04 退款结果通知

1. 接口地址

http//:[ip]:[port]/[project]/app/receiveRefundResult

1. 接口说明

定时退款通知

1. 请求参数

退款结果通知请求参数详见表61。

1. 退款结果通知请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| orderNo | string | 订单号 |
| refundType | string | 00: 用户提交退款  01：超时未使用自动退款 |
| refundResult | string | SUCCESS/FAIL/PROCESSING  SUCCESS—退款成功  FAIL—退款失败  PROCESSING-退款进行中 |
| refundResultDesc | string | 退款描述 |
| refundDate | string | 退款日期 |
| refundAmount | String | 退款金额 单位分 |
| orderType | String | 订单类型  单程票0  普通日票1  全城通日票2 |

1. 应答参数

退款结果通知应答参数详见表62。

1. 退款结果通知应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

* 1. IF8B-05 支付结果通知

1. 接口地址

http//:[ip]:[port]/[project]/app/receivePaymentResult

1. 接口说明

支付结果回调

1. 请求参数

支付结果通知请求参数详见表63。

1. 支付结果通知请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| userId | string | 用户编码 |
| orderNo | string | 订单号 |
| tradeNo | string | 支付交易流水号 |
| payResult | string | SUCCESS/FAIL |
| payAmount | string | 支付金额 |
| payDate | string | 支付时间 |
| voucher | string | 取票凭证 整段用于生成二维码  预留字段 |
| orderType | String | 订单类型  单程票0  普通日票1  全城通日票2 |

1. 应答参数

支付结果通知应答参数详见表64。

1. 支付结果通知应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

* 1. IF8B-06 出票成功结果通知

1. 接口地址

http//:[ip]:[port]/[project]/app/receiveTakeTicketResult

1. 接口说明

当面付出票结果通知

1. 请求参数

出票成功结果通知请求参数详见表65。

1. 出票成功结果通知请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| orderNo | string | 订单号 |
| orderTicketNum | string | 订单数量 |
| actualTakeTicketNum | string | 实际出票数量 |
| takeTickeDate | string | 出票时间 |

1. 应答参数

出票成功结果通知应答参数详见表66。

1. 出票成功结果通知应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

* 1. IF8B-06 签约结果通知（规范中编号存在重复）

1. 接口地址

http//:[ip]:[port]/[project]/app/receiveSignResult

1. 接口说明

返回签约结果通知

1. 请求参数

签约结果通知请求参数详见表67。

1. 签约结果通知请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| thirdUserId | String | 第三方用户编码 |
| requestSignSeq | string | 签约请求流水号 |
| paymentVendor | string | 支付通道编码 |
| payAccountId | string | 第三方签约账号编码 |
| payAgreementNo | string | 第三方签约流水号 |
| signResult | string | SUCCESS签约成功  FAIL签约失败 |
| realNameAuthResult | string | SUCCESS 实名成功  FAIL实名失败 |

1. 应答参数

签约结果通知应答参数详见表68。

1. 签约结果通知应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

* 1. IF8B-07 出票故障结果通知

1. 接口地址

http//:[ip]:[port]/[project]/app/receiveTakeTicketFaultResult

1. 接口说明

出票故障结果通知

1. 请求参数

出票故障结果通知请求参数详见表69。

1. 出票故障结果通知请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| orderNo | string | 订单号 |
| orderTicketNum | string | 订单数量 |
| actualTakeTicketNum | string | 实际出票数量 |
| takeTickeDate | string | 出票时间 |
| takeTiketFaultReason | string | 出票故障原因 |
| refundAmount | String | 退款金额 |

1. 应答参数

出票故障结果通知应答参数详见表70。

1. 出票故障结果通知应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

* 1. IF8B-08 闸机CA公钥变更通知

1. 接口地址

http//:[ip]:[port]/[project]/app/receiveChangeAgmCaKey

1. 接口说明

闸机更换CA公钥后通知APP\_SERVER,APP\_SERVER收到CA公钥变更通知后通知客户端更换用户密钥。

1. 请求参数

闸机CA公钥变更通知请求参数详见表71。

1. 闸机CA公钥变更通知请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| keyBathNumber | string | 密钥批次号 |
| changeDate | string | 变更日期  YYYYMMDDHHMMSS |

1. 应答参数

闸机CA公钥变更通知应答参数详见表72。

1. 闸机CA公钥变更通知应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

* 1. if8a\_26 – 请求补款下单 (新增)

1. 接口地址

http//:[ip]:[port]/[project] /app/requestPayOrder

1. 接口说明

请求生成在线补款订单，在线补款成功后，ITP需要更新补款单中的原乘车订单的状态。

1. 请求参数

请求补款下单请求参数详见表73。

1. 请求补款下单请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| goodsCode | string | 商品编码 固定为001 |
| price | string | 价格(分) |
| quantity | String | 固定为1 |
| orderNoList | array | 单号数组 |

1. 应答参数

请求补款下单应答参数详见表74。

1. 请求补款下单应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |
| orderNo | String | 补款单号 |

* 1. if8a\_29 – 查询用户上次行程 (新增)

1. 接口地址

http//:[ip]:[port]/[project] /app/queryUserItinerary

1. 接口说明

查询用户最近一次行程信息。

1. 请求参数

查询用户上次行程请求参数详见表75。

1. 查询用户上次行程请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| cardNum | string | 卡号 |
| cardType | string | 卡类型 |
| phone | String | 手机号 |
| status | String | 进出站  53查上次进站+进站更新  54查上次出站+出站更新 |
| level | String | 钱包折扣 |

1. 应答参数

查询用户上次行程应答参数详见表76。

1. 查询用户上次行程应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |
| memberItinerary | object | 行程信息对象 |
| memberItinerary对象详情 | | |
| payStatus | String | 是否已生成订单  00未生成  01已生成 |
| thisStationName | String | 本次乘车车站名称 |
| thisTransTime | String | 本次行程时间 |
| thisStationCode | String | 本次乘车车站编码 |
| lastStationName | String | 上次乘车车站名（本次是出站时，此字段填入进站站名，本次为进站时，此字段为空） |
| lastTransTime | String | 上次行程时间（本次是出站时，此字段填入进站时间，本次为进站时，此字段为空） |
| lastStationCode | String | 上次乘车车站编码（本次是出站时，此字段填入进站车站，本次为进站时，此字段为空） |
| transSeq | String | 行程序列号 |
| transValue | Int | 交易金额(分) |
| overtimeTransValue | Int | 超时金额（分） |
| ticketStatus | String | 车票状态  08 20分钟内免费更新 |
| payChannel | String | 支付渠道 |
| oriTicketAmt | Int | 原票价 |
| debitAmt | Int | 扣费金额 |
| orderExpType | Int | 订单异常类型 |
| discountInfo | String | 优惠信息 |
| orderNo | String | 支付单号 |
| carbonDiscount | Int | 碳抵扣优惠金额（分） |

* 1. if8a\_32 – 查询当前是否是单边(新增)

1. 接口地址

http//:[ip]:[port]/[project] /app/ requestSingleTrans

1. 接口说明

查询卡状态，判断是否是进站单边，给用户做是否需要补站提醒。

1. 请求参数

查询当前是否是单边请求参数详见表77。

1. 查询当前是否是单边请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| cardNum | string | 卡号 |

1. 应答参数

查询当前是否是单边应答参数详见表78。

1. 查询当前是否是单边应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |
| show | String | 今天日期是否大于上次进站日期  01 是 |
| stationName | String | 站点名称 |
| transTime | String | 交易时间 |

* 1. if8a\_34 – 获取订单详情 (新增)

1. 接口地址

http//:[ip]:[port]/[project] /app/requestTransDetail

1. 接口说明

查询订单详情。

1. 请求参数

查询订单详情请求参数详见表79。

1. 获取订单详情请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| thirdUserId | String | 用户id |
| orderNo | String | 单号 |

1. 应答参数

查询订单详情应答参数详见表80。

1. 获取订单详情应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |
| ticketTransRecord | String | 订单详情数据 |

ticketTransRecord详情

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| entryStationName | String | 进站名称 |
| entryDate | String | 进站时间YYYYMMDDHH24mmss |
| exitStationName | String | 出站名称 |
| exitDate | String | 出站时间YYYYMMDDHH24mmss |
| payAmount | String | 支付金额（单位分） |
| orderExpType | String | 订单异常类型  0 正常  1 单边账(入站)  2 单边账(出站)  3 单边入站(人工处理单)  4 单边出站(人工处理单)  5 双段计费正常订单\_行程超时 |
| tradeOrderNo | String | 商户订单号 |
| payTradeOrderNo | String | 支付交易订单号 |
| payOrderNoDate | String | 扣款时间 |
| payChannelCode | String | 2.7 支付通道编码 |
| debitRequestResult | String | 扣款结果 |
| discountFee | Int | 优惠金额（分） |
| discountInfo |  | 优惠详情（json数组） |
| companionFlag | String | 同行票标识 Y/N |
| cardNum | String | 卡号 |
| ticketCode | String | 日票的票号 |
| countingTimes | Int | 计次票扣减次数 |
| countingFlag | String | 是否是计次票 Y/N |
| offlineFlag | String | 离线码标识 |
| attributableParty | String | 订单应收商户 |
| receivingParty | String | 订单实收商户 |

* 1. if8a\_35 – 查询用户账务信息 (新增)

1. 接口地址

http//:[ip]:[port]/[project] /app/ requestUserAccInfo

1. 接口说明

查询用户账务信息，包含未支付订单数量，扣费失败订单数量。

1. 请求参数

查询用户账务信息请求参数详见表81。

1. 查询用户账务信息请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| thirdUserId | string | 用户id |

1. 应答参数

查询用户账务信息应答参数详见表82。

1. 查询用户账务信息答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |
| unpaidCount | int | 未支付订单数 |
| failureCount | int | 扣费失败订单数量 |

* 1. if8a\_36 – 请求移除签约信息 (新增)

1. 接口地址

http//:[ip]:[port]/[project] /app/ requestAgreeRelease

1. 接口说明

和解约不同，解约ITP需要请求支付系统，移除时ITP不需要请求支付系统，调用这个接口一般是因为用户协议在第三方那边已失效了，或者是钱包解绑。

1. 请求参数

请求移除签约信息请求参数详见表83。

1. 请求移除签约信息请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| agreementCode | string | 协议编码 |

1. 应答参数

请求移除签约信息应答参数详见表84。

1. 请求移除签约信息答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

* 1. if8a\_37 – 查询购票订单详情 (新增 待补充)

1. 接口地址

http//:[ip]:[port]/[project] /app/requestPayOrderDetail

1. 接口说明

使用地铁APP扫一扫功能，扫描TVM购票机的二维码，通过手机进行支付。

1. 请求参数

查询购票订单详情请求参数详见表85。

1. 查询购票订单详情请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| orderNo | string | 协议编码 |

1. 应答参数

查询购票订单详情应答参数详见表86。

1. 查询购票订单详情答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

* 1. if8a\_38 – 验证是否有符合条件的行程 (新增)

1. 接口地址

http//:[ip]:[port]/[project] /app/ requestTransByChannel

1. 接口说明

给PR停车场，某些第三方使用的功能。

1. 请求参数

验证是否有符合条件的行程请求参数详见表87。

1. 验证是否有符合条件的行程请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| thirdUid | string | 用户id |
| start | String | 开始时间 yyyyMMddHHmmss 非必填 |
| end | String | 结束时间 yyyyMMddHHmmss 非必填 |
| channel | String | 支付方式 |

1. 应答参数

验证是否有符合条件的行程应答参数详见表88。

1. 验证是否有符合条件的行程应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 0000成功，查不到返回失败就可以 |
| retMsg | string | 返回消息 |

* 1. if8a\_41 – 查询账单统计 (新增)

1. 接口地址

http//:[ip]:[port]/[project] /app/ requestTransStatistics

1. 接口说明

查询行程订单数量、金额统计，显示在乘车记录上方

1. 请求参数

查询账单统计请求参数详见表89。

1. 查询账单统计请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| cardId | string | 卡号，支持多个，逗号隔开 |
| startDate | String | 开始时间 yyyyMMdd |
| endDate | String | 结束时间 yyyyMMdd |
| thirdUserId | String | 用户id |

1. 应答参数

查询账单统计应答参数详见表90。

1. 查询账单统计应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |
| tripData | Object | 统计数据对象 |

tripData详情

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| totalPrice | String | 总金额（元） |
| totalDebit | string | 总支付（元） |
| totalDiscount | Object | 总优惠（元） |
| count | Int | 订单数量 |

* 1. if8a\_42– 用户销户 (新增)

1. 接口地址

http//:[ip]:[port]/[project] /app/ requestTransStatistics

1. 接口说明

用户请求销户，清除、转移相关数据。

1. 请求参数

用户销户请求参数详见表91。

1. 用户销户请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| phone | string | 手机号 |
| thirdUserId | String | 用户ID |

1. 应答参数

用户销户应答参数详见表92。

1. 用户销户应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

* 1. if8a\_43– 查询月度账单 (新增)

1. 接口地址

http//:[ip]:[port]/[project] /app/ queryTravelBillStatistics

1. 接口说明

查询用户月度统计数据。

1. 请求参数

查询月度账单请求参数详见表93。

1. 查询月度账单请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| thirdUserId | String | 用户ID |
| startDate | String | 开始时间 yyyyMMdd |
| endDate | String | 结束时间 yyyyMMdd |

1. 应答参数

查询月度账单应答参数详见表94。

1. 查询月度账单答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |
| travelBillData | Object | 账单数据对象 |

travelBillData详情

|  |  |  |
| --- | --- | --- |
| metroTotalAmount | String | 地铁出行总消费金额 |
| metroTotalSaveAmount | String | 地铁出行总节省金额 |
| expectTotalAmount | String | 预计总消费金额 |
| expectMetroTotalSaveAmount | String | 预计地铁出行总节省金额 |
| metroTotalCount | Int | 地铁出行总次数 |
| lastTravelDate | String | 最晚出行日期 yyyy.MM-dd HH:mm |
| lastTravelEnding | String | 最晚出行出站站点名称 |
| discountComparison | String | 折扣节省占比 |

* 1. if8a\_60– 日票下单 (新增)

1. 接口地址

http//:[ip]:[port]/[project] /app/ requestCountingOrder

1. 接口说明

虚拟电子多日票下单。

1. 请求参数

日票下单请求参数详见表93。

1. 日票下单请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| cardType | String | 票卡类型 |
| userId | String | 用户ID |
| ticketPrice | Int | 票价(分) |
| orderSource | String | 下单渠道  1地铁app  4 海上巴士 （下单时，订单支付状态直接为成功，订单状态为已支付） |
| showType | String | 车票展示类型 |

1. 应答参数

日票下单应答参数详见表94。

1. 日票下单应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |
| orderNo | String | ITP单号，目前前缀是0E |

* 1. if8a\_61– 日票支付 (新增)

1. 接口地址

http//:[ip]:[port]/[project] /app/payment/requestPay

1. 接口说明

虚拟电子多日票支付。

1. 请求参数

日票支付请求参数详见表97。

1. 日票支付请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| orderNo | String | 订单号 |
| payChannelCode | String | 支付渠道编码 |
| thirdUserId | String | 用户ID |
| channelType | String | 支付渠道 |
| phone | String | 手机号 |
| orderType | String | 订单类型  1日票  2旅游票 |

1. 应答参数

日票支付应答参数详见表98。

1. 日票支付应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |
| paymentInfo | String | 支付信息 |
| discountInfo | Object | 优惠信息对象 |

discountInfo详情

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| name | string | 优惠名称 |
| amt | Int | 优惠金额（分） |
| type | String | 优惠类型  1券  2活动 |

* 1. if8a\_62– 日票支付结果查询 (新增)

1. 接口地址

http//:[ip]:[port]/[project] /app/payment/ requestPayResult

1. 接口说明

虚拟电子多日票支付结果查询。

1. 请求参数

日票支付结果查询请求参数详见表99。

1. 日票支付结果查询请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| orderNo | String | 订单号 |
| payChannelCode | String | 支付渠道编码 |
| thirdUserId | String | 用户ID |
| channelType | String | 支付渠道 |
| phone | String | 手机号 |
| orderType | String | 订单类型  1日票  2旅游票 |

1. 应答参数

日票支付结果查询应答参数详见表100。

1. 日票支付结果查询应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |
| payResult | String | 支付结果(success/failed) |
| payChannel | String | 支付渠道 |
| payAmount | Int | 支付金额(分) |
| couponDiscount | Int | 优惠券优惠金额(分) |
| channelDiscount | Int | 渠道优惠金额(分) |
| discountInfo | Object | 优惠信息 |
| payDate | String | 支付时间yyyy-MM-dd HH:mm:ss |

discountInfo详情

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| name | string | 优惠名称 |
| amt | Int | 优惠金额（分） |
| type | String | 优惠类型  1券  2活动 |

* 1. if8a\_64 日票退款 (新增)

1. 接口地址

http//:[ip]:[port]/[project] /app/payment/ requestRefundTicket

1. 接口说明

虚拟电子多日票退款

1. 请求参数

日票退款请求参数详见表101。

1. 日票退款请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| orderNo | String | 订单号 |
| orderType | String | 订单类型  1日票  2旅游票 |

1. 应答参数

日票退款应答参数详见表102。

1. 日票退款应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

* 1. if8a\_65 日票取消订单（新增）

1. 接口地址

http//:[ip]:[port]/[project] /app/ticket/ cancelOrder

1. 接口说明

虚拟电子多日票取消订单

1. 请求参数

日票取消订单请求参数详见表103

1. 日票取消订单请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| orderNo | String | 订单号 |
| orderType | String | 订单类型  1日票  2旅游票 |

1. 应答参数

日票取消订单应答参数详见表104

1. 日票取消订单应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

* 1. if8a\_67日票激活（新增）

1. 接口地址

http//:[ip]:[port]/[project] /app/ticket/ updateTicket

1. 接口说明

虚拟电子多日票激活

1. 请求参数

日票激活请求参数详见表105

1. 日票激活请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| ticketCode | String | 票码 |
| countingStart | String | 计次开始时间 |
| countingEnd | String | 计次结束时间 |
| actualTimes | int | 实际次数 |
| cardNum | String | 卡号 |
| thirdUserId | String | 用户ID |
| transSeq | String | 固定0 |
| transDate | String | 交易日期 |
| payChannel | String | 支付渠道 |
| transAmount | String | 交易金额(分) |
| discountAmount | String | 优惠金额(分) |
| operationDate | String | 操作日期(yyyyMMdd) |
| ticketType | String | 票类型 |
| orderNo | String | 订单号 |
| period | String | 有效期(天) |
| cardIssue | String | 发卡机构 |
| ticketName | String | 票名称 |
| showType | String | 展示类型 |

1. 应答参数

日票取消订单应答参数详见表106

1. 日票取消订单应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

* 1. if8a\_70 - 请求旅游票下单 (新增)

1. 接口地址

http//:[ip]:[port]/[project] /app/ticket/ requestTravelOrder

1. 接口说明

请求旅游票下单,旅游票为聚合单，内含多张日票。

1. 请求参数

请求旅游票下单请求参数详见表107。

1. 请求旅游票下单请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| cardType | String | 票卡类型 |
| userId | String | 用户id |
| ticketPrice | int | 票价(分) |
| ticketCount | Int | 购买数量 |
| totalAmount | int | 总金额(分) |
| orderSource | String | 订单来源 |
| showType | String | 展示类型 |

1. 应答参数

请求旅游票下单应答参数详见表108。

1. 请求旅游票下单应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |
| orderNo | String | 旅游票单号 |
| subOrders | String | 每一张票的子单号 |

* 1. if8a\_71 - 通知acc车票已使用 (新增)

1. 接口地址

http//:[ip]:[port]/[project] /app/ticket/updateAndNotice

1. 接口说明

车票使用后通知acc,车票已发售。（日后新ITP如果不想提供接口，可以自己在过闸后判断并通知acc）

1. 请求参数

通知acc车票已使用请求参数详见表109。

1. 通知acc车票已使用请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| cardNum | String | 卡号 |
| countingEnd | String | 计次结束时间 |
| discountAmount | Int | 优惠金额(分) |
| period | int | 有效期(天) |
| ticketName | String | 票名称 |

1. 应答参数

通知acc车票已使用应答参数详见表110。

1. 通知acc车票已使用应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

* 1. if8a\_72 - 小程序票状态同步 (新增)

1. 接口地址

http//:[ip]:[port]/[project] /app/ticket/syncOrder

1. 接口说明

小程序票状态同步,小程序票支付不走ITP的支付系统，订单支付、退款状态从小程序端变更后通知ITP。

1. 请求参数

小程序票状态同步请求参数详见表111。

1. 小程序票状态同步请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| orderNo | String | 单号 |
| orderType | String | 订单类型  1日票  2旅游票 |
| event | String | 事件  1支付成功  2退款 |
| eventTime | date | 事件发生时间 |
| payChannel | String | 支付渠道 |
| discountInfo | String | 优惠信息 |
| payAmount | Int | 支付金额（分） |
| discountAmount | Int | 优惠金额（分） |

1. 应答参数

小程序票状态同步应答参数详见表112。

1. 小程序票状态同步应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

* 1. if8a\_73 - 查询黑名单 (新增)

1. 接口地址

http//:[ip]:[port]/[project] /app/ticket/ queryBlackList

1. 接口说明

主动查询用户黑名单状态以及欠费笔数。

1. 请求参数

查询黑名单请求参数详见表113。

1. 查询黑名单请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| cardId | String | 卡号(可多个,逗号分隔) |

1. 应答参数

查询黑名单应答参数详见表114。

1. 查询黑名单应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |
| inBlack |  | 是否在黑名单  0否  1是 |
| failedCount | Int | 失败次数 |

* 1. if8a\_73 - 请求免费下单(新增)

1. 接口地址

http//:[ip]:[port]/[project] /app/ticket/ requestOrderFree

1. 接口说明

有些车票是通过兑换码兑换的、有些是直接发放的，无需支付，下单即为支付成功

1. 请求参数

请求免费下单请求参数详见表115。

1. 请求免费下单请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| userId | String | 用户ID |
| cardType | String | 票卡类型 |
| ticketPrice | String | 票价 |
| orderSource | String | 订单来源 |
| showType | String | 展示类型 |
| orderType | String | 订单类型  1日票  2旅游票 |
| payChannelCode | String | 支付方式  01手动发放  02兑换码 |

1. 应答参数

请求免费下单应答参数详见表116。

1. 请求免费下单应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |
| orderNo | String | 单号 |
| subOrders | String | 每一张票的子单号 |

* 1. if8a\_76 – 更换手机号(新增)

1. 接口地址

http//:[ip]:[port]/[project] /app/changePhone

1. 接口说明

更换用户的手机号,目前我们更换了用户表、普通卡池表、日票卡池表的数据。

1. 请求参数

更换手机号请求参数详见表117。

1. 更换手机号请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| newPhone | String | 新手机号 |
| thirdUserId | String | 用户id |

1. 应答参数

更换手机号应答参数详见表118。

1. 更换手机号应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

* 1. if8a\_77 – 更换第三方渠道码默认支付方式(新增)

1. 接口地址

http//:[ip]:[port]/[project] /app/requestUpdateChannelDefaultContract

1. 接口说明

更换第三方渠道码默认支付方式，主要用于其他城市sdk。

1. 请求参数

更换第三方渠道码默认支付方式请求参数详见表117。

1. 更换第三方渠道码默认支付方式请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| thirdUserId | String | 用户id |
| channel | String | 支付渠道 |
| cardIssueCode | String | 第三方渠道 |
| regSignSeq | String | 签约流水号 |

1. 应答参数

更换第三方渠道码默认支付方式应答参数详见表118。

1. 更换第三方渠道码默认支付方式应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

* 1. if8a\_75 – 直接解绑支付方式(新增)

1. 接口地址

http//:[ip]:[port]/[project] /app/unbindAgreement

1. 接口说明

普通请求解绑的接口只是提交申请，变更为解约中状态，等待账期结束后执行定时任务解绑。这个接口的场景是用户长时间未登录，需要强制解除绑定关系，所以直接请求支付渠道进行解绑。

1. 请求参数

直接解绑支付方式请求参数详见表117。

1. 直接解绑支付方式请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| thirdUserId | String | 用户id |
| paymentVendor | String | 支付渠道 |
| requestSignSeq | String | 签约流水号 |

1. 应答参数

直接解绑支付方式应答参数详见表118。

1. 直接解绑支付方式应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

* 1. if8d\_03 - 获取离线码数据 (新增)

1. 接口地址

http//:[ip]:[port]/[project] /app/requestNoSignalData

1. 接口说明

获取离线码数据，如果当前用户未进站，则获取下一个序列号的进出站行业数据；如果用户已进站，则返回用户当前序列号的出站行业数据。

1. 请求参数

获取离线码数据请求参数详见表75。

1. 获取离线码数据请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| thirdUserId | String | 用户id |
| cardId | String | 卡号 |
| cardType | String | 卡类型 |

1. 应答参数

获取离线码数据应答参数详见表76。

1. 获取离线码数据应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |
| exitData | String | 出站行业数据 |
| entryData | String | 进站数据 |
| channel | String | ITP的默认支付渠道 |

* 1. 卡片行程通知 (新增)

1. 接口地址

http//:[ip]:[port]/[project] /app/ receiveCardTran

1. 接口说明

用户过闸、补站后，ITP给app推送该笔行程。

1. 请求参数

卡片行程通知请求参数详见表125。

1. 卡片行程通知请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| thirdUserId | String | 用户ID |
| trxType | String | 本次交易类型  "01"：进站  "02"：出站  "03"：超时出站 |
| ticketStatus | String | 车票状态  "02"：结束行程  "03"：SJT发售  "04"：已进站  "05"：已出站  "06"：超时出站  "08"：20分钟内免费更新  "09"：20分钟外付费更新  "10"：入站码更新  "80"：自助补出站  "81"：自助补进站 |
| signChannelCode | String | 支付渠道编码 |
| cardId | String | 逻辑卡号 |
| cardType | String | 卡类型  "0441"：二维码后付费单程票  "0442"：HCE 后付费单程票  "0443"：新版 HCE 后付费单程票  "0444"：员工票  "0445"：一日票  "0446"：三日票  "0447"：七日票  "0448"：月票  "044A"：爱山东 |
| handleDateTime | String | 处理时间  yyyyMMddHHmmss |
| handleStationCode | String | 站点编码 |
| trxAmount | String | 本次交易金额  以“分”为单位的整数字符串 |
| overtimeAmount | String | 超时附加金额  以“分”为单位的整数字符串 |
| lastTicketStatus | String | 上一次票卡状态  "02"：结束行程  "03"：SJT发售  "04"：已进站  "05"：已出站  "06"：超时出站  "08"：20分钟内免费更新  "09"：20分钟外付费更新  "10"：入站码更新  "80"：自助补出站  "81"：自助补进站 |
| lastHandleStationCode | String | 上一次处理车站编码 |
| lastHandleDateTime | String | 上一次处理时间  yyyyMMddHHmmss |
| tikcetTransSeq | String | 票卡交易序列号 |
| deviceId | String | 设备编号 |
| channelType | String | 渠道类型  "00"：闸机  "01"：蓝牙  "02"：BOM  "03"：自助补站 |
| companionFlag | String | 同行票是Y，第三方是C，目前两种标识对应的卡类型都是0441 非必填 |
| countingFlag | String | 日票标识 |

1. 应答参数

卡片行程通知应答参数详见表126。

1. 卡片行程通知应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

* 1. 行程订单通知 (新增)

1. 接口地址

http//:[ip]:[port]/[project] /app/ orderPayNotice

1. 接口说明

用户行程订单产生支付结果后（无论失败还是成功），ITP将行程订单数据推送到APP。

1. 请求参数

行程订单通知请求参数详见表127。

1. 行程订单通知通知请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| thirdUserId | String | 第三方用户ID |
| entryStationName | String | 进站站点名称 |
| exitStationName | String | 出站站点名称 |
| entryStationDate | String | 进站日期 |
| exitStationDate | String | 出站日期 |
| payAmount | String | 支付金额 |
| tradeOrderNo | String | 交易订单号 |
| payTradeOrderNo | String | 支付交易订单号 |
| orderExpType | String | 订单类型 |
| payChannelCode | String | 支付渠道代码 |
| orderStatus | String | 订单状态 |
| requestDebitNo | String | 请求扣款编号 |
| cardNo | String | 卡号 |
| ticketType | String | 票种类型 |
| transSeq | String | 交易序列号 |
| debitAmount | String | 扣款金额 |

1. 应答参数

行程订单通知应答参数详见表128。

1. 行程订单通知通知应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

* 1. 多日票次数扣减通知 (新增)

1. 接口地址

http//:[ip]:[port]/[project] /app/receiveCountingTicketTimes

1. 接口说明

多日票次数扣减通知。

1. 请求参数

多日票次数扣减通知请求参数详见表129。

1. 多日票次数扣减通知请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| thirdUserId | String | 第三方用户ID |
| cardId | String | 卡号 |
| transSeq | String | 交易序列号 |
| times | String | 扣减次数（最大值为2） |

1. 应答参数

多日票次数扣减通知应答参数详见表130。

1. 多日票次数扣减通知应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

* 1. 接收签约异常状态通知 (新增)

1. 接口地址

http//:[ip]:[port]/[project] /app/receiveAgreementException

1. 接口说明

ITP扣费失败后，判断扣费失败原因，如果是因为签约异常，将异常信息通知到APP。

1. 请求参数

接收签约异常状态通知请求参数详见表131。

1. 接收签约异常状态通知请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| thirdUserId | String | 第三方用户ID |
| cardId | String | 卡号 |
| transSeq | String | 交易序列号 |
| times | String | 扣减次数（最大值为2） |

1. 应答参数

接收签约异常状态通知应答参数详见表132。

1. 接收签约异常状态通知应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

* 1. 征信状态更新(新增)

1. 接口地址

http//:[ip]:[port]/[project] /app/ credit

1. 接口说明

ITP扣费成功后，主动调用支付系统查询征信，征信如果有问题，推送到APP。

1. 请求参数

征信状态更新请求参数详见表133。

1. 征信状态更新请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| thirdUserId | String | 第三方用户ID |
| payChannelCode | String | 支付渠道 |

1. 应答参数

征信状态更新应答参数详见表134。

1. 征信状态更新应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

* 1. 5分钟客流数据更新(新增)

1. 接口地址

http//:[ip]:[port]/[project] /receive/passengerFlow

1. 接口说明

ITP每五分钟向APP推送每个车站五分钟内的进站客流。

1. 请求参数

5分钟客流数据更新请求参数详见表135。

1. 5分钟客流数据更新请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| passengerData | String | 客流数据，AES加密后的字符串。解密后为JSON对象，Map格式：key为站点代码，value为该站点客流量（Integer）。必填 |
| timeSeq | String | 时间序列，用于标识数据批次顺序。当timeSeq=1时会清理数据库。必填 |
| timeStart | String | 开始时间 必填 |
| timeEnd | String | 结束时间 必填 |
| transType | String | 进出站类型 必填 |
| transDate | String | 日期 必填 |

1. 应答参数

5分钟客流数据更新应答参数详见表134。

1. 5分钟客流数据更新应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

* 1. 支付宝出行-添加签约信息(新增)

1. 接口地址

http//:[ip]:[port]/[project] /channel/addContract

1. 接口说明

添加支付宝出行签约信息。

1. 请求参数

添加签约信息请求参数详见表137。

1. 添加签约信息请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| channel | String | 支付渠道 |
| thirdUserId | String | 用户ID |
| agreementCode | String | 签约协议号，系统生成的唯一协议编号 |
| channelAgreementCode | String | 渠道协议号，第三方渠道的协议编号 |
| channelUserAccount | String | 渠道用户账户，用户在第三方渠道的账户标识 |
| cardIssueCode | String | 发卡类型代码，如：0007 |

1. 应答参数

添加签约信息应答参数详见表138。

1. 添加签约信息应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

* 1. 支付宝出行-解约登记（销卡）(新增)

1. 接口地址

http//:[ip]:[port]/[project]/channel/terminateContract

1. 接口说明

请求解约，ITP账期结束后执行解约任务。

1. 请求参数

解约登记请求参数详见表139。

1. 解约登记请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| agreementCode | String | 协议号，签约时生成的协议编号 |
| merchantNo | String | 合作方机构编号/商户号 |

1. 应答参数

解约登记应答参数详见表140。

1. 解约登记应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

* 1. 支付宝出行-开卡申请(新增)

1. 接口地址

http//:[ip]:[port]/[project] /channel/requestApplication

1. 接口说明

支付宝出行申请开卡

1. 请求参数

开卡申请请求参数详见表141。

1. 开卡申请请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| thirdUserId | String | 第三方用户ID，格式化后的用户标识 |
| cardType | String | 卡片类型，如：02 |
| msisdn | String | 用户手机号码 |
| extend1 | String | 扩展字段1 |
| extend2 | String | 扩展字段2（可选） |
| cardIssueCode | String | 发卡渠道代码 0007支付宝出行 |

1. 应答参数

开卡申请应答参数详见表142。

1. 开卡申请应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |
| cardId | String | 卡片ID/逻辑卡号，开卡成功后返回 |

* 1. 支付宝出行-获取行业数据(新增)

1. 接口地址

http//:[ip]:[port]/[project] /memberContract/channel/requestIndustryData

1. 接口说明

获取支付宝出行行业数据。

1. 请求参数

获取行业数据请求参数详见表143。

1. 获取行业数据请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| thirdUserId | String | 第三方用户ID |
| cardId | String | 卡片ID/逻辑卡号 |
| cardType | String | 卡片类型 |

1. 应答参数

获取行业数据应答参数详见表144。

1. 获取行业数据应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

* 1. 支付宝出行-查询乘车记录(新增)

1. 接口地址

http//:[ip]:[port]/[project] /channel/ findTravelList

1. 接口说明

查询支付宝出行乘车记录。

1. 请求参数

查询支付宝出行乘车记录请求参数详见表145。

1. 查询乘车记录请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| thirdUserId | String | 第三方用户ID，格式化后的用户标识 |
| page | String | 页码，从 0 开始 |
| size | String | 每页大小 |
| debitRequestResult | String | 扣款请求结果筛选（可选） |
| invoice | String | 发票状态筛选（可选） |
| startDate | String | 开始日期（可选） |
| endDate | String | 结束日期（可选） |

1. 应答参数

查询支付宝出行乘车记录参数详见表146。

1. 查询乘车记录应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |
| pageNumber | Int | 当前页码 |
| pageSize | Int | 每页大小 |
| totalPage | Int | 总页数 |
| totalCount | Int | 总记录数 |
| ticketTransRecord | Array | 乘车记录列表 |

**ticketTransRecord单条记录**

|  |  |  |
| --- | --- | --- |
| entryStationName | String | 进站站点名称 |
| entryDate | String | 进站时间 |
| exitStationName | String | 出站站点名称 |
| exitDate | String | 出站时间 |
| payAmount | String | 实付金额（单位：分） |
| totalAmount | String | 总金额（单位：分） |
| orderExpType | String | 订单扩展类型 |
| tradeOrderNo | String | 交易订单号 |
| payTradeOrderNo | String | 支付交易订单号 |
| payOrderNoDate | String | 支付订单日期 |
| payChannelCode | String | 支付渠道代码 |
| debitRequestResult | String | 扣款请求结果 |
| discountFee | String | 优惠金额 |
| discountInfo | String | 优惠信息 |
| companionFlag | String | 同行票标识 |
| cardNum | String | 卡号 |
| ticketCode | String | 日票票号 |
| countingTimes | String | 计次次数（预留） |
| countingFlag | String | 计次标识（预留） |

* 1. 支付宝出行-查询乘车记录详情(新增)

1. 接口地址

http//:[ip]:[port]/[project] /channel/findTravelDetail

1. 接口说明

查询支付宝出行乘车记录详情。

1. 请求参数

查询乘车记录详情请求参数详见表147。

1. 查询乘车记录详情请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| thirdUserId | String | 第三方用户ID，格式化后的用户标识 |
| orderNo | String | 订单号 |

1. 应答参数

查询支付宝出行乘车记录详情参数详见表148。

1. 查询乘车记录详情应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |
| entryStationName | String | 进站站点名称 |
| entryDate | String | 进站时间 |
| exitStationName | String | 出站站点名称 |
| exitDate | String | 出站时间 |
| payAmount | String | 实付金额（单位：分） |
| totalAmount | String | 总金额（单位：分） |
| orderExpType | String | 订单扩展类型 |
| tradeOrderNo | String | 交易订单号 |
| payTradeOrderNo | String | 支付交易订单号 |
| payOrderNoDate | String | 支付订单日期 |
| payChannelCode | String | 支付渠道代码 |
| debitRequestResult | String | 扣款请求结果 |
| discountFee | String | 优惠金额 |
| discountInfo | String | 优惠信息 |
| companionFlag | String | 同行票标识 |
| cardNum | String | 卡号 |
| ticketCode | String | 日票票号 |
| countingTimes | String | 计次次数（预留） |
| countingFlag | String | 计次标识（预留） |

* 1. 支付宝出行-业务关闭结果通知(新增)

1. 接口地址

http//:[ip]:[port]/[project]/notify/closeResultForAlipay

1. 接口说明

支付宝出行销卡结果通知。

1. 请求参数

业务关闭结果通知请求参数详见表149。

1. 业务关闭结果通知请求参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| result | Boolean | 是否同意关闭，true-同意，false-拒绝 |
| agreementNo | String | 协议号 |

1. 应答参数

业务关闭结果通知应答参数详见表150。

1. 业务关闭结果通知应答参数信息

|  |  |  |
| --- | --- | --- |
| 字段 | 类型 | 说明 |
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

* 1. 支付宝出行-行程数据推送(新增)

1. 接口地址

http//:[ip]:[port]/[project]/notify/pushTransData

1. 接口说明

支付宝出行行程数据推送。

1. 请求参数

支付宝出行行程数据推送与APP行程数据通知参数相同。

1. 应答参数

支付宝出行行程数据推送应答参数使用通用响应。

* 1. 支付宝出行-行业数据推送(新增)

1. 接口地址

http//:[ip]:[port]/[project]/notify/receiveCardDataFromItp

1. 接口说明

支付宝出行行业数据推送。

1. 请求参数

支付宝出行行业数据推送与APP行业数据通知参数相同。

1. 应答参数

支付宝出行行业数据推送应答参数使用通用响应。

* 1. 支付宝出行-黑名单状态变更通知(新增)

1. 接口地址

http//:[ip]:[port]/[project]/notify/ receiveBlackListFromItp

1. 接口说明

支付宝出行黑名单状态变更通知。

1. 请求参数

支付宝出行黑名单状态变更通知与APP黑名单状态变更通知参数相同。

1. 应答参数

支付宝出行黑名单状态变更通知应答参数使用通用响应。