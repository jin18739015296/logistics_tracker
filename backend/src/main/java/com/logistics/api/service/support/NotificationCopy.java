package com.logistics.api.service.support;

import org.springframework.util.StringUtils;

/**
 * App 内消息中心（通知）的标题与正文，集中维护便于统一语气与后续多语言。
 * <p>
 * <b>下发策略（站内信 notification 表）</b>：同一业务动作只对「需要异步知晓的一方」写入消息中心，避免用户与配送员重复收到同质信息。
 * <ul>
 *   <li><b>下单成功待支付</b> → 仅下单用户（寄件人账号）</li>
 *   <li><b>支付成功</b> → 仅下单用户</li>
 *   <li><b>配送员已接单（含运力匹配接单）</b> → 仅下单用户（对用户只说「已接单/上门取件」，不提抢单池等业务词）</li>
 *   <li><b>系统派单</b> → 配送员侧走 MQ/WebSocket 实时通道，不写用户消息中心（用户已在支付成功文案中知晓「正在安排配送」）</li>
 *   <li><b>配送员已揽件 / 已送达</b> → 仅下单用户（配送员为操作方）</li>
 *   <li><b>客户评价配送员</b> → 仅被评价的配送员账号</li>
 *   <li><b>投诉提交</b> → 投诉人确认 + 管理员待办；受理阶段不通知配送员，避免核实前重复打扰</li>
 *   <li><b>投诉处理结案</b> → 投诉人 + 关联配送员（若有）各一份结论文案</li>
 * </ul>
 */
public final class NotificationCopy {

    private NotificationCopy() {
    }

    public static String orderCreatedTitle() {
        return "订单已创建，请尽快完成支付";
    }

    public static String orderCreatedBody(String orderNo) {
        return "您好，您的寄件订单已成功提交，目前处于「待支付」状态。\n\n"
                + "【订单号】" + orderNo + "\n\n"
                + "请您在「我的订单」或订单详情页完成支付。支付成功后，我们会尽快为您匹配配送员，并安排上门取件时间与路线。\n\n"
                + "【您可以这样做】\n"
                + "· 核对寄件人、收件人与物品信息是否正确；\n"
                + "· 如需修改地址或物品信息，建议在未支付前取消订单后重新下单；\n"
                + "· 若暂时无法支付，可先保存订单，避免重复填写。\n\n"
                + "温馨提示：订单长时间未支付可能会被系统自动关闭，届时需重新下单。";
    }

    public static String paymentSuccessTitle() {
        return "支付成功，订单已进入配送准备";
    }

    public static String refundSuccessTitle() {
        return "退款已到账，请查收钱包余额";
    }

    public static String refundSuccessBody(String orderNo, java.math.BigDecimal amount) {
        String amt = amount != null ? amount.setScale(2, java.math.RoundingMode.HALF_UP).toString() : "0.00";
        return "您的订单退款已处理完成，相应款项已退回至您的钱包余额中，请注意查收。\n\n"
                + "【订单号】" + (orderNo != null ? orderNo : "—") + "\n"
                + "【退款金额】¥" + amt + "\n\n"
                + "退款金额可直接用于后续订单支付，也可在「我的钱包」中查看明细。\n\n"
                + "如有疑问，请联系在线客服。";
    }

    public static String paymentSuccessBody(String orderNo) {
        return "我们已收到您的款项，支付顺利完成，感谢您的选择与信任。\n\n"
                + "【订单号】" + orderNo + "\n\n"
                + "当前订单状态已更新，平台正在为您匹配就近、可用的配送员。匹配成功后，您可以在订单详情页实时查看配送进度；配送员也可能通过电话与您确认上门时间、楼栋门禁或停车位置等细节。\n\n"
                + "【建议您留意】\n"
                + "· 保持预留手机号畅通，避免错过配送员来电；\n"
                + "· 若有改址、改约、代收等特殊需求，请尽早通过订单页联系客服说明；\n"
                + "· 快件交接时请当面清点外包装是否完好，贵重物品建议拍照留存。\n\n"
                + "后续物流节点更新也会在订单详情中展示，方便您随时跟踪。";
    }

    /** 用户侧：运力接单成功（不把「抢单」「派单」等内部说法写给用户） */
    public static String grabOrderUserTitle() {
        return "配送员已接单，即将与您联系";
    }

    public static String grabOrderUserBody(String orderNo, String courierName) {
        String name = StringUtils.hasText(courierName) ? courierName : "配送员";
        return "好消息：您的订单已由配送员接单，对方将根据订单信息与您约定上门取件时间。\n\n"
                + "【订单号】" + orderNo + "\n"
                + "【为您服务】" + name + "\n\n"
                + "【建议您配合】\n"
                + "· 请保持下单时预留的手机号畅通，便于确认详细地址、楼层与门禁方式；\n"
                + "· 请提前准备好待寄物品，避免液体、违禁品等不符合寄递规则的情形；\n"
                + "· 若临时无法在家交接，可与配送员协商代收或改约时间。\n\n"
                + "全部进度与物流节点仍可在订单详情中查看；若有疑问也可随时联系在线客服。";
    }

    /**
     * 配送员侧（主动接单后）：当前不向配送员写入消息中心，保留文案供短信/Push 等扩展。
     */
    public static String grabOrderCourierTitle() {
        return "接单成功，请及时履约";
    }

    public static String grabOrderCourierBody(String orderNo) {
        return "您已成功接单，本单已进入您的今日配送任务列表。\n\n"
                + "【订单号】" + orderNo + "\n\n"
                + "【履约提醒】\n"
                + "· 请尽早致电或通过 App 内信息与寄件人确认上门时间与地点；\n"
                + "· 上门时请核对物品名称、数量与包装是否符合订单描述；\n"
                + "· 取件后请及时在系统中更新状态，便于客户跟踪物流。\n\n"
                + "若因路况、客户原因等无法按时履约，请尽早按规定申请改派或报备，以免影响服务评分与客户体验。";
    }

    public static String pickedUpTitle() {
        return "配送员已取件，快件正在途中";
    }

    public static String pickedUpBody(String orderNo) {
        return "配送员已完成上门取件，快件已从寄件地址发出，正在送往收件地址。\n\n"
                + "【订单号】" + orderNo + "\n\n"
                + "从现在起到签收前，订单详情中的物流节点会随运输进度更新，您可随时打开查看当前环节。\n\n"
                + "【温馨提示】\n"
                + "· 请提醒收件人保持手机畅通，以便派送时联系；\n"
                + "· 易碎、贵重或生鲜类物品，建议收件时当面检查外包装与封签；\n"
                + "· 若长时间未见节点更新，可通过订单页联系客服为您核实。\n\n"
                + "感谢您使用本平台寄递服务，我们会尽量保障运输过程透明、可查。";
    }

    public static String deliveredTitle() {
        return "快件已送达，请查收";
    }

    public static String deliveredBody(String orderNo) {
        return "您好，您的快件已由配送员送达，系统已将订单标记为「已送达」。\n\n"
                + "【订单号】" + orderNo + "\n\n"
                + "请您核对包裹外观是否完好、数量是否与订单一致。确认无误后，您可在 App 内点击「确认收货」，订单即正式完结。\n\n"
                + "【若发现问题】\n"
                + "· 外包装破损、短缺或与描述不符：请拍照留存并尽快联系客服或发起售后；\n"
                + "· 误投或配送员联系不上您：可与配送员或客服协商二次派送安排。\n\n"
                + "订单完结后，欢迎您对本次配送服务留下真实评价，帮助我们持续提升服务质量。";
    }

    /* ---------- 投诉 / 评价（系统类） ---------- */

    public static String complaintSubmittedUserTitle() {
        return "投诉已受理，我们会认真跟进";
    }

    public static String complaintSubmittedUserBody() {
        return "感谢您抽出时间向我们反馈问题，您的每一条意见都是我们改进服务的重要依据。\n\n"
                + "我们已收到您的投诉内容，工单已进入处理队列。客服人员会在工作时间内查阅订单记录、配送轨迹及相关凭证，并与涉及方核实情况，力求给出公正、清晰的结论。\n\n"
                + "【您可以这样配合】\n"
                + "· 在「投诉与反馈」中留意处理进度更新；\n"
                + "· 暂时保留聊天记录截图、通话记录、破损照片等材料；\n"
                + "· 处理结束后我们会通过消息通知告知结果，届时请查阅正文中的结论与说明。\n\n"
                + "再次感谢您的理解与耐心，我们会尽快给您答复。";
    }

    /**
     * 投诉已提交（配送员侧）：当前不向配送员发「投诉已提交」站内信，仅在管理员结案后发结论通知；文案保留供扩展。
     */
    public static String complaintSubmittedCourierTitle() {
        return "您有一条订单需要配合核实";
    }

    public static String complaintSubmittedCourierBody(String orderNo) {
        return "用户就关联订单发起了投诉，平台可能稍后与您核实履约细节。\n\n"
                + "【订单号】" + orderNo + "\n\n"
                + "【建议准备】\n"
                + "· 回顾本单取件、运输与派送时间节点；\n"
                + "· 整理与客户沟通的通话或消息记录（如有）；\n"
                + "· 若涉及货损或延误，准备现场照片、签收底单等材料。\n\n"
                + "正式结论以平台结案通知为准；若需申诉，请在收到处理结果后按指引提交材料。";
    }

    public static String complaintSubmittedAdminTitle() {
        return "新投诉待处理，请及时分派";
    }

    public static String complaintSubmittedAdminBody(String orderNo) {
        return "管理后台收到一条新的用户投诉，当前状态为「待处理」。\n\n"
                + "【关联订单号】" + orderNo + "\n\n"
                + "【处理建议】\n"
                + "· 优先查看投诉类型、标题与用户上传的附件；\n"
                + "· 对照订单详情与物流事件时间线，核对是否存在服务瑕疵或异常节点；\n"
                + "· 在承诺时效内完成核实，填写处理结论并同步至用户端消息通知；\n"
                + "· 若涉及配送员责任认定，请留存完整处置记录以便复盘。\n\n"
                + "及时响应有助于降低工单积压与用户重复进线。";
    }

    /** 用户评价后通知配送员（用户端评价接口） */
    public static String userReviewCourierTitle() {
        return "您收到一条新的客户评价";
    }

    public static String userReviewCourierBody(String orderNo, Integer rating, String reviewContent) {
        int r = rating != null ? rating : 0;
        StringBuilder sb = new StringBuilder();
        sb.append("客户已完成对本订单服务的评价。评分与评语将纳入您的服务档案，用于后续服务质量分析与用户参考。\n\n");
        sb.append("【订单号】").append(orderNo != null ? orderNo : "—").append("\n");
        sb.append("【星级】").append(r).append(" 星\n");
        if (StringUtils.hasText(reviewContent)) {
            String shortText = reviewContent.length() > 120 ? reviewContent.substring(0, 120) + "…" : reviewContent;
            sb.append("【评语摘录】").append(shortText).append("\n");
        }
        sb.append("\n【您可以】\n");
        sb.append("· 在评价中心查看完整内容与历史评价趋势；\n");
        sb.append("· 结合订单回顾沟通与履约细节，总结可改进之处；\n");
        sb.append("· 若评语中存在误解，可在允许范围内通过平台渠道礼貌澄清。\n\n");
        sb.append("持续优质的服务有助于获得更多订单机会与用户信任。");
        return sb.toString();
    }

    /** 配送员端评价接口写入时的通知正文 */
    public static String courierReviewReceivedBody(String orderNo, Integer rating) {
        int r = rating != null ? rating : 0;
        return "您收到一条新的客户评价，系统已为您归档。\n\n"
                + "【订单号】" + (orderNo != null ? orderNo : "—") + "\n"
                + "【星级】" + r + " 星\n\n"
                + "评价详情可在「我的评价」或评价中心中查看。建议您对照订单时间节点与客户备注，思考哪些方面做得好、哪些可以优化——高分评语是对服务的肯定，中肯的建议同样是宝贵的改进方向。\n\n"
                + "感谢您的辛勤配送，祝下一单顺利。";
    }

    /* ---------- 投诉结案（投诉人 / 配送员） ---------- */

    /** 投诉结案 · 投诉人 · 标题 */
    public static String complaintResultTitleForComplainant(boolean resolved) {
        return resolved ? "投诉处理完成：您的诉求得到支持" : "投诉处理完成：本次暂不支持您的诉求";
    }

    /** 投诉结案 · 配送员 · 标题 */
    public static String complaintResultTitleForCourier(boolean resolved) {
        return resolved ? "投诉核查结论：成立（请对照改进）" : "投诉核查结论：未成立（请知悉）";
    }

    public static String complaintResultBodyIntroForComplainant() {
        return "您好，感谢您此前的耐心等候。平台已对您提交的投诉完成核查，现就核实结论向您说明如下。\n\n";
    }

    public static String complaintResultFooterForComplainant() {
        return "\n若您对上述结论仍有疑问，可在有效期内通过客服渠道补充材料或申请复核，我们会依据规则再次审视。\n\n"
                + "您的监督帮助我们不断完善规则与服务流程，再次感谢您的反馈。";
    }

    public static String complaintResultIntroForCourier() {
        return "平台已对用户投诉完成核查，并形成书面结论。下列信息与您的履约行为相关，请您认真阅读并作为后续服务的参考。\n\n";
    }

    public static String complaintResultFooterForCourier() {
        return "\n若您认为结论与事实有重大出入，可在配送员端按平台公示的规则与时限提交申诉材料（含说明与凭证），我们会安排专人复核。\n\n"
                + "规范、透明的处置有利于保护用户与配送员双方合法权益，感谢您的理解与配合。";
    }

    public static String complaintResultLabelEstablished() {
        return "成立";
    }

    public static String complaintResultLabelNotEstablished() {
        return "未成立";
    }
}
