import urllib.request
import urllib.parse
import json

def fetch_svg(puml_text, filename):
    url = "https://kroki.io/plantuml/svg"
    req = urllib.request.Request(url, data=puml_text.encode('utf-8'))
    req.add_header('Content-Type', 'text/plain')
    req.add_header('User-Agent', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)')
    try:
        response = urllib.request.urlopen(req)
        svg_content = response.read()
        with open(filename, "wb") as f:
            f.write(svg_content)
        print(f"Generated {filename}")
    except Exception as e:
        print(f"Error on {filename}: {e}")

plantuml_header = """
@startuml
skinparam style strictuml
skinparam SequenceMessageAlign center
skinparam defaultFontName sans-serif
skinparam backgroundColor transparent
"""

ssds = {
    "ssd_uc01": """
actor 用户
participant 系统

用户 -> 系统 : SubmitChargingRequest(userId, vehicleId, mode, targetAmount)
系统 --> 用户 : ReturnQueueInfo(requestId, queueType, queueNo, estimatedWaitTime)
用户 -> 系统 : QueryQueueStatus(requestId)
系统 --> 用户 : ReturnCurrentStatus(requestId, queueNo, estimatedWaitTime)
    """,
    "ssd_uc02": """
actor 用户
participant 系统

用户 -> 系统 : QueryQueueStatus(requestId)
系统 --> 用户 : ReturnCurrentStatus(requestId, queueNo, estimatedWaitTime)
    """,
    "ssd_uc03": """
actor 用户
participant 系统

用户 -> 系统 : ModifyChargingRequest(requestId, newMode, newTargetAmount)
系统 --> 用户 : ReturnModifiedQueueInfo(requestId, queueType, newQueueNo, estimatedWaitTime)
用户 -> 系统 : QueryQueueStatus(requestId)
系统 --> 用户 : ReturnCurrentStatus(requestId, queueNo, estimatedWaitTime)
    """,
    "ssd_uc04": """
actor 用户
participant 系统

用户 -> 系统 : CancelChargingRequest(requestId)
系统 --> 用户 : ReturnCancelResult(success)
    """,
    "ssd_uc05": """
actor 充电桩设备
participant 系统

充电桩设备 -> 系统 : ReportPileReady(pileId, vehicleId)
系统 --> 充电桩设备 : AcknowledgeReady()
充电桩设备 -> 系统 : ReportChargeStart(pileId, startTime)
系统 --> 充电桩设备 : AcknowledgeStart()
    """,
    "ssd_uc06": """
actor 用户
participant 系统

用户 -> 系统 : RequestBilling(requestId)
系统 --> 用户 : ReturnBill(billId, chargeFee, serviceFee, totalFee)
用户 -> 系统 : ConfirmPayment(billId, paymentMethod)
系统 --> 用户 : ReturnPaymentResult(result, finishTime)
    """,
    "ssd_uc07": """
actor 用户
participant 系统

用户 -> 系统 : ViewBills(userId, timeRange)
系统 --> 用户 : ReturnBillList(billList)
    """,
    "ssd_uc08": """
actor 管理员
participant 系统

管理员 -> 系统 : ViewAllPileStatus()
系统 --> 管理员 : ReturnAllPileStatus(pileList, statusTypes)
    """,
    "ssd_uc09": """
actor 管理员
participant 系统

管理员 -> 系统 : ViewFaultInfo(pileId)
管理员 -> 系统 : HandlePileFault(pileId, strategyType)
系统 --> 管理员 : ReturnRescheduleResult(affectedRequestCount, repairStatus)
管理员 -> 系统 : ViewPileStatus(pileId)
系统 --> 管理员 : ReturnPileStatus(pileId, currentStatus)
    """,
    "ssd_uc10": """
actor 管理员
participant 系统

管理员 -> 系统 : GenerateReport(timeRange, reportType)
系统 --> 管理员 : ReturnReportData(reportDetails, statistics)
    """,
    "ssd_uc11": """
actor 管理员
participant 系统

管理员 -> 系统 : ViewBillingRules()
系统 --> 管理员 : ReturnBillingRules(currentRules)
管理员 -> 系统 : UpdateBillingRules(newServiceFee, newTimeRates)
系统 --> 管理员 : ReturnUpdateResult(success)
    """
}

for name, body in ssds.items():
    puml = plantuml_header + body + "\n@enduml"
    fetch_svg(puml, name + ".svg")
